package com.hanseongge.global.auth;

import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * 회원 JWT 발급/검증 (개발계획 §7.1).
 *
 * <h2>audience = "user"</h2>
 * 관리자 토큰({@code aud="admin"})과 서명키를 공유하지만 audience 가 달라 서로의 API 를
 * 호출할 수 없다. 경로 단위(필터체인)와 토큰 단위(여기) <b>양쪽</b>에서 막힌다 —
 * 나중에 경로 설정을 잘못 건드려도 한 겹이 남는다.
 *
 * <h2>Refresh 토큰이 JWT 가 아닌 이유</h2>
 * 회원 Refresh 는 서명 문자열이 아니라 <b>무작위 값</b>이고, DB 에 SHA-256 해시로 저장된다.
 * JWT 로 만들면 자체 검증이 가능해 편하지만, 그만큼 서버가 회수할 수단이 없어진다.
 * 14일짜리 토큰이 탈취되면 14일 내내 유효하다. 로그아웃·탈퇴·정지가 즉시 먹혀야 해서
 * 상태를 가진 형태를 택했다. Access 토큰(30분)만 무상태 JWT 다.
 */
@Slf4j
@Component
public class UserJwtProvider {

    /** 회원 토큰의 audience. 관리자 토큰과 구분하는 핵심 값이다. */
    public static final String AUDIENCE_USER = "user";

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    /** Refresh 토큰 원문 바이트 수. 128비트를 넘겨 추측을 실질적으로 불가능하게 한다. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public UserJwtProvider(AppProperties appProperties, JwtSigningKey signingKey) {
        AppProperties.Jwt config = appProperties.jwt();
        this.issuer = signingKey.issuer();
        this.key = signingKey.key();
        this.accessTtl = Duration.ofMinutes(config.userAccessTtlMinutes());
        this.refreshTtl = Duration.ofDays(config.userRefreshTtlDays());
    }

    // ---------- 발급 ----------

    public String issueAccessToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .audience().add(AUDIENCE_USER).and()
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Refresh 토큰 원문.
     *
     * <p>이 값은 <b>이 순간에만 존재한다.</b> 호출자가 쿠키로 내보내고 나면 서버에는
     * 해시만 남는다. 다시 만들어낼 수 없으므로 분실 시 재로그인 외에 방법이 없다 —
     * 그것이 의도다.
     */
    public String issueRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    // ---------- 검증 ----------

    /**
     * Access 토큰을 검증하고 회원 ID 를 돌려준다.
     *
     * @throws BusinessException 만료({@code TOKEN_EXPIRED}) 또는 그 외 모든 부적합
     *                           ({@code TOKEN_INVALID}). 실패 사유를 응답에서 세분화하지
     *                           않는다 — "서명은 맞는데 audience 가 틀렸다" 같은 힌트를
     *                           주지 않기 위해서다. 상세는 로그에만 남는다
     */
    public Long parseAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "만료된 회원 토큰");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] 회원 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "회원 토큰 검증 실패");
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(AUDIENCE_USER)) {
            log.debug("[JWT] audience 불일치. expected={} actual={}", AUDIENCE_USER, audience);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "회원 토큰이 아님");
        }
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "토큰 종류 불일치");
        }

        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "subject 가 회원 ID 형식이 아님");
        }
    }
}
