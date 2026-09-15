package com.hanseongge.global.auth;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * 관리자 JWT 발급/검증 (설계서 §10).
 *
 * <h2>audience = "admin"</h2>
 * 사용자 토큰(STEP 6, {@code aud="user"})과 서명키를 공유하더라도 audience 가 다르면
 * 서로의 API 를 호출할 수 없다. 이 검사는 {@code /api/admin/**} 전용 필터체인과 함께
 * <b>이중</b>으로 걸린다 — 나중에 경로 설정을 잘못 건드려도 토큰 단에서 한 번 더 막힌다.
 *
 * <h2>tv (token version)</h2>
 * 무상태 JWT 는 발급 후 회수할 수단이 없다. 관리자 토큰은 탈취 시 피해가 커서 그 상태를
 * 두지 않는다. 로그아웃·비밀번호 변경·권한/상태 변경이 {@code admin_users.token_version}
 * 을 올리고, 필터가 매 요청 대조한다. 세션 테이블을 따로 만들지 않고 회수 가능성을 얻는다.
 */
@Slf4j
@Component
public class AdminJwtProvider {

    /** 관리자 토큰의 audience. 사용자 토큰과 구분하는 핵심 값이다. */
    public static final String AUDIENCE_ADMIN = "admin";

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private static final Duration OTP_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final Duration OTP_SETUP_TTL = Duration.ofMinutes(15);

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AdminJwtProvider(AppProperties appProperties, JwtSigningKey signingKey) {
        AppProperties.Jwt config = appProperties.jwt();
        this.issuer = signingKey.issuer();
        this.accessTtl = Duration.ofMinutes(config.adminAccessTtlMinutes());
        this.refreshTtl = Duration.ofHours(config.adminRefreshTtlHours());
        // 서명키는 회원 토큰과 공유한다. 구분은 키가 아니라 audience 가 한다.
        this.key = signingKey.key();
    }

    // ---------- 발급 ----------

    public String issueAccessToken(AdminUser admin) {
        return issue(admin, AdminTokenType.ACCESS, accessTtl);
    }

    public String issueRefreshToken(AdminUser admin) {
        return issue(admin, AdminTokenType.REFRESH, refreshTtl);
    }

    /** 로그인 2단계 — 6자리 입력용. 짧게 둔다: 할 일은 휴대폰을 꺼내 숫자를 보는 것뿐이다. */
    public String issueOtpChallengeToken(AdminUser admin) {
        return issue(admin, AdminTokenType.OTP_CHALLENGE, OTP_CHALLENGE_TTL);
    }

    /** 로그인 2단계 — 첫 등록용. 인증 앱을 설치하고 QR 을 찍을 시간을 준다. */
    public String issueOtpSetupToken(AdminUser admin) {
        return issue(admin, AdminTokenType.OTP_SETUP, OTP_SETUP_TTL);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private String issue(AdminUser admin, AdminTokenType type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(admin.getLoginId())
                .audience().add(AUDIENCE_ADMIN).and()
                .id(java.util.UUID.randomUUID().toString())
                .claim(CLAIM_ROLE, admin.getRole().name())
                .claim(CLAIM_TYPE, type.claimValue())
                .claim(CLAIM_TOKEN_VERSION, admin.getTokenVersion())
                // adminId 는 sub 가 아니라 별도 클레임에 둔다. sub(login_id)는 사람이 읽는
                // 값이라 변경 가능성이 있고, 조회 키는 불변인 PK 여야 한다.
                .claim("aid", admin.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    // ---------- 검증 ----------

    /**
     * 토큰 파싱 + 검증.
     *
     * @throws BusinessException 만료({@code TOKEN_EXPIRED}) 또는 그 외 모든 부적합
     *                           ({@code TOKEN_INVALID}). 실패 사유를 응답으로 세분화하지
     *                           않는 이유는, 공격자에게 "서명은 맞는데 audience 가 틀렸다"
     *                           같은 힌트를 주지 않기 위해서다. 상세는 로그에만 남긴다.
     */
    public AdminTokenClaims parse(String token, AdminTokenType expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "만료된 관리자 토큰");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] 관리자 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "관리자 토큰 검증 실패: " + e.getMessage());
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(AUDIENCE_ADMIN)) {
            log.debug("[JWT] audience 불일치. expected={} actual={}", AUDIENCE_ADMIN, audience);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "관리자 토큰이 아님");
        }

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.claimValue().equals(type)) {
            log.debug("[JWT] 토큰 종류 불일치. expected={} actual={}", expectedType.claimValue(), type);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "토큰 종류 불일치");
        }

        AdminRole role = parseRole(claims.get(CLAIM_ROLE, String.class));
        Long adminId = claims.get("aid", Number.class) == null
                ? null
                : claims.get("aid", Number.class).longValue();
        if (adminId == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "adminId 클레임 없음");
        }
        Number tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Number.class);

        return new AdminTokenClaims(
                adminId,
                claims.getSubject(),
                role,
                expectedType,
                tokenVersion == null ? -1 : tokenVersion.intValue());
    }

    private AdminRole parseRole(String value) {
        try {
            return AdminRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            // 권한 등급이 삭제·개명된 뒤 남아 있던 토큰. 권한 없음으로 처리하지 않고 거부한다.
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "알 수 없는 권한 등급: " + value);
        }
    }
}
