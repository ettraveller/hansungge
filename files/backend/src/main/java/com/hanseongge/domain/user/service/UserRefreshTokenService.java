package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.RefreshToken;
import com.hanseongge.domain.user.repository.RefreshTokenRepository;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 회원 Refresh 토큰의 발급·회전·폐기와 쿠키 처리.
 *
 * <h2>왜 쿠키인가</h2>
 * 14일짜리 토큰을 자바스크립트가 읽을 수 있는 곳(localStorage)에 두면 XSS 한 번에 장기
 * 세션이 통째로 넘어간다. HttpOnly 쿠키는 스크립트가 읽지 못한다. 반대로 Access
 * 토큰(30분)은 응답 본문으로 주고 메모리에만 둔다 — 짧고, 매 요청 헤더에 실어야 한다.
 *
 * <h2>CSRF</h2>
 * {@code SameSite=Lax} 라 <b>교차 사이트 POST 에는 쿠키가 실리지 않는다.</b> 공격자 페이지가
 * 재발급을 유도해도 쿠키가 가지 않아 실패한다. 운영에서 쇼핑몰(www)과 API(api)는 같은
 * 등록 도메인이라 정상 요청에는 실린다 — 그때는 {@code cookieDomain} 을
 * {@code .hanseongge.com} 으로 준다.
 *
 * <h2>회전</h2>
 * 재발급 때마다 기존 행을 폐기하고 새 토큰을 낸다. 탈취된 토큰이 무한정 살아 있지 않게 하고,
 * 폐기된 토큰이 다시 쓰이면 그 자체가 탈취 신호가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRefreshTokenService {

    /** 이름을 짧고 무의미하게 둔다 — 쿠키 이름이 스택을 알려줄 이유가 없다. */
    public static final String COOKIE_NAME = "hsg_rt";
    /** 인증 경로에만 실린다. 상품·시세 조회 요청에까지 장기 토큰을 실어 보낼 이유가 없다. */
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevoker revoker;
    private final UserJwtProvider jwtProvider;
    private final AppProperties appProperties;

    /** 새 Refresh 토큰을 만들어 저장하고 쿠키로 내보낸다. 원문은 이 순간 이후 사라진다. */
    @Transactional
    public void issue(Long userId, HttpServletResponse response) {
        String raw = jwtProvider.issueRefreshToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(jwtProvider.refreshTtl());
        refreshTokenRepository.save(RefreshToken.issueForUser(userId, raw, expiresAt));
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(raw, jwtProvider.refreshTtl()).toString());
    }

    /**
     * 쿠키의 토큰을 검증하고 회전한다.
     *
     * @return 이 토큰이 가리키는 회원 ID
     */
    @Transactional
    public Long rotate(String rawToken, HttpServletResponse response) {
        RefreshToken token = findUsable(rawToken);
        token.revoke();
        issue(token.getSubjectId(), response);
        return token.getSubjectId();
    }

    /** 이 기기의 세션만 끊는다. 토큰이 이미 없거나 만료됐어도 조용히 성공시킨다. */
    @Transactional
    public void revoke(String rawToken, HttpServletResponse response) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository
                    .findByTokenHashAndSubjectType(RefreshToken.hash(rawToken), RefreshToken.SUBJECT_USER)
                    .ifPresent(RefreshToken::revoke);
        }
        // 로그아웃은 실패해서는 안 되는 동작이다. 서버에 기록이 없더라도 브라우저의
        // 쿠키는 반드시 지운다 — 안 그러면 "로그아웃했는데 계속 로그인 상태"가 된다.
        clearCookie(response);
    }

    /** 탈퇴·정지 시 모든 기기에서 끊는다. */
    @Transactional
    public void revokeAll(Long userId) {
        int revoked = refreshTokenRepository.revokeAll(RefreshToken.SUBJECT_USER, userId);
        log.info("[AUTH] 회원 {} 의 세션 {}건을 폐기했습니다.", userId, revoked);
    }

    private RefreshToken findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "리프레시 토큰 없음");
        }
        Optional<RefreshToken> found = refreshTokenRepository
                .findByTokenHashAndSubjectType(RefreshToken.hash(rawToken), RefreshToken.SUBJECT_USER);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "알 수 없는 리프레시 토큰");
        }
        RefreshToken token = found.get();
        if (!token.isUsable(OffsetDateTime.now())) {
            // 이미 폐기된 토큰이 다시 온 것은 탈취 신호일 수 있다. 해당 회원의 세션을
            // 전부 끊어 피해를 끊는다 — 정상 사용자는 다시 로그인하면 된다.
            //
            // 폐기를 별도 트랜잭션에서 커밋한다. 같은 트랜잭션에서 하면 바로 아래
            // 예외가 폐기까지 되돌려, 조치한 것처럼 보이지만 실제로는 아무 일도
            // 일어나지 않는다.
            if (token.isRevoked()) {
                log.warn("[AUTH] 폐기된 리프레시 토큰 재사용 감지. 회원 {}", token.getSubjectId());
                revoker.revokeAllNow(token.getSubjectId());
            }
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "사용할 수 없는 리프레시 토큰");
        }
        return token;
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        AppProperties.OAuth oauth = appProperties.oauth();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(oauth.refreshCookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge);
        // 비워 두면 host-only 쿠키다. 개발(localhost)에서는 그것이 맞고, 운영에서는
        // www 와 api 서브도메인이 함께 써야 하므로 .hanseongge.com 을 준다.
        if (!oauth.refreshCookieDomain().isBlank()) {
            builder.domain(oauth.refreshCookieDomain());
        }
        return builder.build();
    }
}
