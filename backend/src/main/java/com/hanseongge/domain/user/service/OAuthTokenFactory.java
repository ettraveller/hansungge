package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.global.auth.JwtSigningKey;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 로그인·가입 과정에서만 쓰이는 짧은 토큰들. 전부 서명만 하고 암호화하지 않으므로
 * <b>개인정보를 담지 않는다</b> — 신원은 DB 에 두고 행 id 만 싣는다.
 *
 * <h2>state — 인가코드 주입(CSRF) 방지</h2>
 * 공격자가 자기 인가코드를 피해자 브라우저로 흘려보내면, 피해자가 로그인한 결과가
 * <b>공격자 소셜 계정에 연결된 세션</b>이 된다. state 는 "이 콜백이 우리가 시작한 그 요청인가"를
 * 확인한다. 서버에 저장하지 않고 서명으로 검증하며, 프론트엔드도 자기 sessionStorage 값과
 * 대조하므로 방어가 두 겹이다.
 *
 * <h2>signUpToken — 가입 진행 상태 ({@link PendingSignUp})</h2>
 * 계정을 먼저 만들고 동의를 나중에 받으면 그 사이 개인정보를 동의 없이 보관한 것이 된다.
 * 그래서 진행 상태를 토큰에 담아 두고, 마지막 단계에서 한 트랜잭션으로 계정을 만든다.
 *
 * <h2>resetFlowToken · resetToken — 비밀번호 찾기</h2>
 * 앞의 것은 본인확인 창을 여는 동안 거래번호를 나르고, 뒤의 것은 본인확인을 통과한 뒤
 * <b>5분 동안만</b> 새 비밀번호를 정할 수 있는 권한이다. 일회성은 토큰이 아니라 본인확인 행의
 * 소진 표시({@code identity_verifications.consumed_at}, 트리거가 한 번만 허용)가 보장한다.
 *
 * <h2>identityReturnToken — 본인확인 결과 전달</h2>
 * 공급자가 백엔드 수신 주소로 보낸 값을 화면까지 나른다. 서명은 위변조를 막으려는 것이
 * 아니다 — 누구나 수신 주소를 호출해 토큰을 만들 수 있다. <b>신뢰의 근거는 서버가 공급자에게
 * 직접 되묻는 결과조회와 거래번호 대조</b>이고, 이 토큰은 짧은 만료를 걸기 위해 쓴다.
 */
@Slf4j
@Component
public class OAuthTokenFactory {

    private static final String AUD_STATE = "oauth-state";
    private static final String AUD_SIGNUP = "oauth-signup";
    private static final String AUD_RESET_FLOW = "pw-reset-flow";
    private static final String AUD_RESET = "pw-reset";
    private static final String AUD_IDENTITY_RETURN = "identity-return";

    private static final String CLAIM_PROVIDER = "prv";
    private static final String CLAIM_PROVIDER_USER_ID = "puid";
    private static final String CLAIM_EMAIL = "eml";
    private static final String CLAIM_NAME = "nm";
    /** 약관 동의 내역. {@code {"TERMS": true, "MARKETING": false, ...}} */
    private static final String CLAIM_AGREEMENTS = "agr";
    /** 본인확인 창을 연 거래번호 (이니시스 mTxId, 20자 이하) */
    private static final String CLAIM_IDENTITY_REQUEST = "mtx";
    /** 본인확인 행 id. 본인확인을 마친 뒤에만 있다. */
    private static final String CLAIM_IDENTITY_VERIFICATION = "ivid";
    /** 공급자가 수신 주소로 보낸 값 */
    private static final String CLAIM_RETURNED = "ret";

    /** 거래번호 문자. 이니시스 mTxId 는 20바이트 이하 — 영숫자만 쓴다. */
    private static final char[] REQUEST_ID_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final SecretKey key;
    private final String issuer;
    private final Duration stateTtl;
    private final Duration signUpTtl;
    private final Duration identityTtl;
    private final Duration resetTtl;
    private final SecureRandom random = new SecureRandom();

    public OAuthTokenFactory(AppProperties appProperties, JwtSigningKey signingKey) {
        this.key = signingKey.key();
        this.issuer = signingKey.issuer();
        this.stateTtl = Duration.ofMinutes(appProperties.oauth().stateTtlMinutes());
        this.signUpTtl = Duration.ofMinutes(appProperties.oauth().signUpTokenTtlMinutes());
        this.identityTtl = Duration.ofMinutes(appProperties.signUp().identityTtlMinutes());
        this.resetTtl = Duration.ofMinutes(appProperties.signUp().passwordResetTtlMinutes());
    }

    // ---------- state ----------

    public String issueState(OAuthProviderType provider) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(AUD_STATE).and()
                .id(Base64.getUrlEncoder().withoutPadding().encodeToString(nonce))
                .claim(CLAIM_PROVIDER, provider.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(stateTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * state 검증.
     *
     * <p>제공자까지 대조하는 이유: 구글용으로 발급한 state 를 네이버 콜백에 붙여 보내는
     * 조합을 막는다. 서명만 보면 그 교차 사용이 통과한다.
     */
    public void verifyState(String state, OAuthProviderType expectedProvider) {
        Claims claims = parse(state, AUD_STATE, ErrorCode.OAUTH_STATE_INVALID);
        String provider = claims.get(CLAIM_PROVIDER, String.class);
        if (!expectedProvider.name().equals(provider)) {
            log.debug("[OAUTH] state 제공자 불일치. expected={} actual={}", expectedProvider, provider);
            throw new BusinessException(ErrorCode.OAUTH_STATE_INVALID, "state 제공자 불일치");
        }
    }

    // ---------- 본인확인 거래번호 ----------

    /**
     * 본인확인 창을 열 때마다 새로 만드는 거래번호.
     *
     * <p>이니시스 {@code mTxId} 는 20바이트 이하이고 요청마다 유일해야 한다. 20자 영숫자(헷갈리는
     * 글자 제외 56종)면 경우의 수가 10^35 에 가까워 충돌을 걱정할 필요가 없다.
     */
    public String newIdentityRequestId() {
        char[] out = new char[20];
        for (int i = 0; i < out.length; i++) {
            out[i] = REQUEST_ID_CHARS[random.nextInt(REQUEST_ID_CHARS.length)];
        }
        return new String(out);
    }

    // ---------- signUpToken ----------

    /**
     * 진행 중인 가입 상태를 토큰에 담는다.
     *
     * <p><b>본인확인 결과는 담지 않는다.</b> 신원은 DB 에 두고 그 행의 id 만 담는다
     * ({@link PendingSignUp} 참조).
     */
    public String issuePendingToken(PendingSignUp pending) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .audience().add(AUD_SIGNUP).and()
                .claim(CLAIM_IDENTITY_REQUEST, pending.identityRequestId())
                .claim(CLAIM_IDENTITY_VERIFICATION, pending.identityVerificationId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(signUpTtl)));

        if (pending.hasAgreements()) {
            Map<String, Boolean> agreements = new LinkedHashMap<>();
            pending.agreements().forEach((type, agreed) -> agreements.put(type.name(), agreed));
            builder.claim(CLAIM_AGREEMENTS, agreements);
        }
        if (pending.isSocial()) {
            builder.subject(pending.social().providerUserId())
                    .claim(CLAIM_PROVIDER, pending.social().provider().name())
                    .claim(CLAIM_PROVIDER_USER_ID, pending.social().providerUserId())
                    .claim(CLAIM_EMAIL, pending.social().email())
                    .claim(CLAIM_NAME, pending.social().name());
        }
        return builder.signWith(key).compact();
    }

    /** 가입 토큰에서 진행 상태를 복원한다. 만료·위조면 처음부터 다시 하게 한다. */
    public PendingSignUp readPendingToken(String token) {
        Claims claims = parse(token, AUD_SIGNUP, ErrorCode.SIGNUP_TOKEN_INVALID);

        OAuthUserInfo social = null;
        String providerName = claims.get(CLAIM_PROVIDER, String.class);
        if (providerName != null) {
            OAuthProviderType provider;
            try {
                provider = OAuthProviderType.valueOf(providerName);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "알 수 없는 제공자");
            }
            String providerUserId = claims.get(CLAIM_PROVIDER_USER_ID, String.class);
            if (providerUserId == null || providerUserId.isBlank()) {
                throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "제공자 식별자 없음");
            }
            social = new OAuthUserInfo(provider, providerUserId,
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class));
        }

        return new PendingSignUp(
                social,
                readAgreements(claims),
                claims.get(CLAIM_IDENTITY_REQUEST, String.class),
                readId(claims, CLAIM_IDENTITY_VERIFICATION));
    }

    private Map<AgreementType, Boolean> readAgreements(Claims claims) {
        Object raw = claims.get(CLAIM_AGREEMENTS);
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        map.forEach((type, agreed) -> {
            try {
                agreements.put(AgreementType.valueOf(String.valueOf(type)), Boolean.TRUE.equals(agreed));
            } catch (IllegalArgumentException e) {
                // 약관 종류가 사라졌다 — 토큰이 그 사이에 발급된 것이다. 처음부터 다시.
                throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "알 수 없는 약관: " + type);
            }
        });
        return agreements;
    }

    public long signUpTokenTtlSeconds() {
        return signUpTtl.toSeconds();
    }

    // ---------- 비밀번호 찾기 ----------

    /** 비밀번호 찾기 본인확인 창을 여는 동안 거래번호를 나른다. */
    public String issueResetFlowToken(String identityRequestId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(AUD_RESET_FLOW).and()
                .claim(CLAIM_IDENTITY_REQUEST, identityRequestId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(signUpTtl)))
                .signWith(key)
                .compact();
    }

    /** @return 본인확인 창을 연 거래번호 */
    public String readResetFlowToken(String token) {
        Claims claims = parse(token, AUD_RESET_FLOW, ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        String requestId = claims.get(CLAIM_IDENTITY_REQUEST, String.class);
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "거래번호 없음");
        }
        return requestId;
    }

    /**
     * 본인확인을 통과한 회원이 새 비밀번호를 정할 수 있는 권한 — 5분.
     *
     * <p>회원 id 와 본인확인 행 id 를 함께 담는다. 재설정 시 그 본인확인이 <b>그 회원의 DI</b> 인지,
     * 아직 쓰지 않은 것인지를 DB 에서 다시 확인하고 소진한다.
     */
    public String issueResetToken(Long userId, Long identityVerificationId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(AUD_RESET).and()
                .subject(String.valueOf(userId))
                .claim(CLAIM_IDENTITY_VERIFICATION, identityVerificationId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(resetTtl)))
                .signWith(key)
                .compact();
    }

    public ResetGrant readResetToken(String token) {
        Claims claims = parse(token, AUD_RESET, ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        Long identityVerificationId = readId(claims, CLAIM_IDENTITY_VERIFICATION);
        try {
            Long userId = Long.valueOf(claims.getSubject());
            if (identityVerificationId == null) {
                throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "본인확인 없음");
            }
            return new ResetGrant(userId, identityVerificationId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "회원 식별자 형식 오류");
        }
    }

    public record ResetGrant(Long userId, Long identityVerificationId) {
    }

    // ---------- 본인확인 결과 전달 ----------

    /**
     * 공급자가 수신 주소로 보낸 값을 화면까지 나른다.
     *
     * <p>받은 필드 가운데 {@code allowedKeys} 만 담는다 — 수신 주소는 누구나 호출할 수 있어서,
     * 거르지 않으면 큰 본문을 보내 긴 토큰(=긴 주소)을 만들게 할 수 있다.
     */
    public String issueIdentityReturnToken(Map<String, String> returned, Set<String> allowedKeys) {
        Map<String, String> kept = new LinkedHashMap<>();
        returned.forEach((k, v) -> {
            if (allowedKeys.contains(k) && v != null && v.length() <= 2048) {
                kept.put(k, v);
            }
        });
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(AUD_IDENTITY_RETURN).and()
                .claim(CLAIM_RETURNED, kept)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(identityTtl)))
                .signWith(key)
                .compact();
    }

    public Map<String, String> readIdentityReturnToken(String token) {
        Claims claims = parse(token, AUD_IDENTITY_RETURN, ErrorCode.IDENTITY_VERIFICATION_INVALID);
        Map<String, String> returned = new LinkedHashMap<>();
        if (claims.get(CLAIM_RETURNED) instanceof Map<?, ?> map) {
            map.forEach((k, v) -> returned.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        }
        return returned;
    }

    // ---------- 공통 ----------

    /** JWT 숫자 claim 은 Integer 로 돌아올 수 있다. Long 으로 맞춘다. */
    private Long readId(Claims claims, String name) {
        Number value = claims.get(name, Number.class);
        return value == null ? null : value.longValue();
    }

    /**
     * 만료와 위조를 같은 코드로 돌려준다.
     *
     * <p>어느 토큰이든 "처음부터 다시"가 유일한 조치다. 사유를 나눠 알려주면 공격자에게 어떤
     * 값이 유효한지에 대한 힌트만 준다.
     */
    private Claims parse(String token, String expectedAudience, ErrorCode errorCode) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(errorCode, "토큰 없음");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[OAUTH] {} 검증 실패: {}", expectedAudience, e.getMessage());
            throw new BusinessException(errorCode, expectedAudience + " 검증 실패");
        }
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(expectedAudience)) {
            throw new BusinessException(errorCode, "audience 불일치");
        }
        return claims;
    }
}
