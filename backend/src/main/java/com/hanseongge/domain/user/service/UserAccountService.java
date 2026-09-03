package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.UserResponse;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserAgreement;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.domain.user.repository.UserAgreementRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 회원 계정의 <b>상태를 바꾸는</b> 부분.
 *
 * <h2>왜 {@link UserAuthService} 와 나눠 두는가</h2>
 * 소셜 로그인 흐름 안에는 외부 HTTP 호출(인가코드 교환)이 있다. 그 호출까지 하나의
 * 트랜잭션에 넣으면 제공자가 느릴 때 DB 커넥션을 붙잡은 채 대기하게 되고, 커넥션 풀이
 * 먼저 마른다.
 *
 * <p>그렇다고 같은 클래스 안에서 {@code @Transactional} 메서드를 호출하면 <b>프록시를
 * 거치지 않아 트랜잭션이 조용히 무시된다.</b> 그러면 엔티티 변경(이름·이메일 동기화)이
 * 반영되지 않고, 원인을 찾기도 어렵다. 그래서 빈 자체를 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserAgreementRepository agreementRepository;
    private final AgreementCatalog agreementCatalog;
    private final UserJwtProvider jwtProvider;
    private final UserRefreshTokenService refreshTokenService;

    /** 이미 연동된 소셜 계정으로 로그인. */
    @Transactional
    public SignInResponse signIn(Long socialAccountId, OAuthUserInfo info,
                                 HttpServletResponse response) {
        SocialAccount account = socialAccountRepository.findById(socialAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "소셜 연동 없음: " + socialAccountId));
        User user = userRepository.findById(account.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "소셜 연동은 있으나 회원이 없음: " + account.getUserId()));
        user.requireSignInAllowed();
        // 제공자에서 이름·이메일을 뒤늦게 받은 경우를 채운다. 사용자가 고친 값은 덮지 않는다.
        user.syncFromProvider(info.email(), info.name());
        return issueSession(user, response);
    }

    /**
     * 가입 — 회원·소셜연동·동의이력을 <b>한 트랜잭션</b>에 만든다.
     *
     * <p>중간에 실패하면 아무것도 남지 않아야 한다: 동의 없는 계정도, 계정 없는 동의도
     * 만들지 않는다.
     */
    @Transactional
    public SignInResponse createAccount(OAuthUserInfo info,
                                        Map<AgreementType, Boolean> choices,
                                        String preferredName,
                                        RequestInfo requestInfo,
                                        HttpServletResponse response) {
        // 약관 화면에 머무는 사이 다른 탭에서 이미 가입했을 수 있다.
        Optional<SocialAccount> existing = socialAccountRepository
                .findByProviderAndProviderUserId(info.provider(), info.providerUserId());
        if (existing.isPresent()) {
            log.info("[AUTH] 가입 진행 중 이미 연동됨. 로그인으로 전환. provider={}", info.provider());
            return signIn(existing.get().getId(), info, response);
        }

        String name = preferredName != null && !preferredName.isBlank()
                ? preferredName.trim()
                : info.name();
        User user = userRepository.save(User.signUp(info.email(), name));

        try {
            socialAccountRepository.saveAndFlush(SocialAccount.connect(
                    user.getId(), info.provider(), info.providerUserId(), info.email()));
        } catch (DataIntegrityViolationException e) {
            // ux_social 위반 — 같은 소셜 계정이 동시에 두 번 가입을 시도했다.
            // 삼키면 회원 행만 남은 고아 계정이 생기므로 트랜잭션째 되돌린다.
            log.warn("[AUTH] 소셜 연동 중복. provider={}", info.provider());
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED, "이미 연동된 소셜 계정");
        }

        recordAgreements(user.getId(), choices, requestInfo);
        log.info("[AUTH] 신규 가입 완료. userId={} provider={}", user.getId(), info.provider());
        return issueSession(user, response);
    }

    /** 쿠키 회전 후 새 Access 토큰을 낸다. */
    @Transactional(readOnly = true)
    public SignInResponse describeSession(Long userId, String accessToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requireSignInAllowed();
        return SignInResponse.signedIn(accessToken, jwtProvider.accessTtlSeconds(),
                UserResponse.from(user));
    }

    /**
     * 동의 내역 기록.
     *
     * <p>선택 약관을 아예 보내지 않았어도 {@code agreed=false} 로 남긴다. 기록이 없으면
     * 나중에 "물어봤는데 거절한 것"인지 "묻지 않은 것"인지 구분할 수 없다.
     */
    private void recordAgreements(Long userId, Map<AgreementType, Boolean> choices,
                                  RequestInfo requestInfo) {
        List<UserAgreement> rows = new ArrayList<>();
        for (AgreementType type : AgreementType.values()) {
            boolean agreed = Boolean.TRUE.equals(choices.get(type));
            rows.add(UserAgreement.record(userId, type, agreementCatalog.versionOf(type),
                    agreed, requestInfo == null ? null : requestInfo.ip()));
        }
        agreementRepository.saveAll(rows);
    }

    /** Access 토큰은 본문으로, Refresh 토큰은 HttpOnly 쿠키로 나간다. */
    private SignInResponse issueSession(User user, HttpServletResponse response) {
        refreshTokenService.issue(user.getId(), response);
        return SignInResponse.signedIn(
                jwtProvider.issueAccessToken(user.getId()),
                jwtProvider.accessTtlSeconds(),
                UserResponse.from(user));
    }
}
