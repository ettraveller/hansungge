package com.hanseongge.domain.user.service;

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
import java.util.Set;

/**
 * 소셜 로그인 과정에서만 쓰이는 두 개의 짧은 토큰.
 *
 * <h2>state — 인가코드 주입(CSRF) 방지</h2>
 * 공격자가 자기 인가코드를 피해자 브라우저로 흘려보내면, 피해자가 로그인한 결과가
 * <b>공격자 소셜 계정에 연결된 세션</b>이 된다. 그 세션에서 피해자가 결제하면 공격자
 * 계정에 주문이 쌓인다. state 는 "이 콜백이 우리가 시작한 그 요청인가"를 확인한다.
 *
 * <p>서버에 저장하지 않고 서명으로 검증한다. 임시 테이블이나 세션을 두면 무상태 구조가
 * 깨지고, 만료 정리 배치가 하나 더 생긴다. 프론트엔드도 자기 sessionStorage 값과
 * 대조하므로 방어가 두 겹이다.
 *
 * <h2>signUpToken — 약관 동의 전 임시 신원</h2>
 * 소셜 인증은 끝났지만 <b>아직 회원이 아닌</b> 상태를 나른다. 계정을 먼저 만들고 동의를
 * 나중에 받으면, 그 사이 개인정보를 동의 없이 보관한 것이 된다. 그래서 인증 결과를
 * DB 가 아니라 서명된 토큰에 담아 두고, 동의가 끝나는 순간 한 트랜잭션에서 계정을 만든다.
 */
@Slf4j
@Component
public class OAuthTokenFactory {

    private static final String AUD_STATE = "oauth-state";
    private static final String AUD_SIGNUP = "oauth-signup";
    private static final String CLAIM_PROVIDER = "prv";
    private static final String CLAIM_PROVIDER_USER_ID = "puid";
    private static final String CLAIM_EMAIL = "eml";
    private static final String CLAIM_NAME = "nm";

    private final SecretKey key;
    private final String issuer;
    private final Duration stateTtl;
    private final Duration signUpTtl;
    private final SecureRandom random = new SecureRandom();

    public OAuthTokenFactory(AppProperties appProperties, JwtSigningKey signingKey) {
        AppProperties.OAuth oauth = appProperties.oauth();
        this.key = signingKey.key();
        this.issuer = signingKey.issuer();
        this.stateTtl = Duration.ofMinutes(oauth.stateTtlMinutes());
        this.signUpTtl = Duration.ofMinutes(oauth.signUpTokenTtlMinutes());
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
     * <p>제공자까지 대조하는 이유: 구글용으로 발급한 state 를 카카오 콜백에 붙여 보내는
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

    // ---------- signUpToken ----------

    public String issueSignUpToken(OAuthUserInfo info) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(AUD_SIGNUP).and()
                .subject(info.providerUserId())
                .claim(CLAIM_PROVIDER, info.provider().name())
                .claim(CLAIM_PROVIDER_USER_ID, info.providerUserId())
                .claim(CLAIM_EMAIL, info.email())
                .claim(CLAIM_NAME, info.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(signUpTtl)))
                .signWith(key)
                .compact();
    }

    /** 가입 토큰에서 소셜 신원을 복원한다. 만료·위조면 다시 로그인부터 하게 한다. */
    public OAuthUserInfo readSignUpToken(String token) {
        Claims claims = parse(token, AUD_SIGNUP, ErrorCode.SIGNUP_TOKEN_INVALID);
        OAuthProviderType provider;
        try {
            provider = OAuthProviderType.valueOf(claims.get(CLAIM_PROVIDER, String.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "알 수 없는 제공자");
        }
        String providerUserId = claims.get(CLAIM_PROVIDER_USER_ID, String.class);
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "제공자 식별자 없음");
        }
        return new OAuthUserInfo(provider, providerUserId,
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class));
    }

    public long signUpTokenTtlSeconds() {
        return signUpTtl.toSeconds();
    }

    // ---------- 공통 ----------

    /**
     * 만료와 위조를 같은 코드로 돌려준다.
     *
     * <p>state 는 어차피 "처음부터 다시"가 유일한 조치이고, 가입 토큰도 마찬가지다.
     * 사유를 나눠 알려주면 공격자에게 어떤 값이 유효한지에 대한 힌트만 준다.
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
