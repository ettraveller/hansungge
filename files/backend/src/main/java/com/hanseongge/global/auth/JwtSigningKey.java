package com.hanseongge.global.auth;

import com.hanseongge.global.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 관리자·회원 토큰이 공유하는 서명키.
 *
 * <p><b>왜 한 곳에 모으는가.</b> 두 토큰 제공자가 각자 키를 만들면, 시크릿이 비어 있는
 * 개발 환경에서 서로 다른 1회용 랜덤 키가 만들어진다. 그 자체로 고장은 아니지만
 * "왜 어떤 토큰만 안 되지"를 추적하기 어렵게 만들고, 나중에 키 교체(rotation)를 붙일 때
 * 고쳐야 할 곳이 둘로 갈린다.
 *
 * <p>키를 공유해도 두 토큰은 섞이지 않는다. 구분은 키가 아니라
 * <b>audience</b>({@code admin} / {@code user})가 하고, 필터체인이 경로 단위로 한 번 더 나눈다.
 */
@Slf4j
@Component
public class JwtSigningKey {

    private final SecretKey key;
    private final String issuer;

    public JwtSigningKey(AppProperties appProperties) {
        AppProperties.Jwt config = appProperties.jwt();
        this.issuer = config.issuer();
        this.key = resolve(config);
    }

    public SecretKey key() {
        return key;
    }

    public String issuer() {
        return issuer;
    }

    /**
     * 시크릿이 없으면 기동 시 1회용 랜덤 키를 만든다.
     *
     * <p>재기동하면 발급된 토큰이 전부 무효가 되지만, <b>고정된 기본 키를 코드에
     * 박아두는 것보다 낫다</b> — 그런 키는 반드시 운영까지 따라간다. 운영(prod)은
     * {@code app.jwt.secret: ${JWT_SECRET}} 이 기본값 없이 선언되어 있어 여기까지 오지 않는다.
     */
    private SecretKey resolve(AppProperties.Jwt config) {
        if (!config.hasSecret()) {
            log.warn("""
                    [JWT] app.jwt.secret 이 비어 있어 1회용 랜덤 서명키로 기동합니다.
                    재기동하면 발급된 토큰이 모두 무효가 됩니다.
                    고정하려면 JWT_SECRET 환경변수를 설정하세요 (최소 32바이트).""");
            return Jwts.SIG.HS256.key().build();
        }
        try {
            return Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException e) {
            // 짧은 키를 조용히 늘려주지 않는다. 서명 강도를 시스템이 임의로 정하면 안 된다.
            throw new IllegalStateException(
                    "JWT_SECRET 이 너무 짧습니다. HS256 은 최소 32바이트(256bit)가 필요합니다.", e);
        }
    }
}
