package com.hanseongge.domain.user.identity;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * CI → 판정 키 (같은 사람 판정에 쓰는 값).
 *
 * <p>{@code identity_key = HMAC-SHA256(pepper, CI)} 를 16진수 64자로.
 *
 * <h2>왜 CI 원본을 두지 않나</h2>
 * 간편인증의 CI 는 이니시스가 "비교검증 후 파기"를 권고하는 값이고, 정보통신망법 §23조의5·6 으로
 * CI 이용기관에 직접 의무가 있다. 같은 사람인지 비교만 하면 되므로 되돌릴 수 없는 값으로 충분하다.
 *
 * <h2>왜 무작위 salt 가 아니라 고정 pepper 인가</h2>
 * 비교가 목적이라 같은 CI 는 언제나 같은 키가 나와야 한다. 대신 비밀값을 섞어, DB 만 새어 나가서는
 * 다른 곳의 CI 와 맞춰 볼 수 없게 한다. 비밀값은 환경변수로만 들어온다.
 *
 * <p>{@code SHA-256(CI + pepper)} 가 아니라 HMAC 인 이유: 비밀값을 섞는 표준 구성이라 길이 확장 같은
 * 해시 구성의 약점을 따질 필요가 없다. 목적(결정적·비가역·비밀값 의존)은 같다.
 *
 * <p>⚠️ <b>pepper 를 바꾸면 기존 판정 키와 전부 어긋난다</b> — 이미 가입한 사람이 다시 가입해도
 * 알아보지 못한다.
 */
@Slf4j
@Component
public class IdentityKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_PEPPER_LENGTH = 32;

    private final byte[] pepper;

    public IdentityKeyHasher(AppProperties appProperties) {
        String value = appProperties.identity().hashPepper();
        this.pepper = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (pepper.length == 0) {
            log.warn("[IDENTITY] IDENTITY_HASH_PEPPER 가 비어 있습니다. 본인인증(자체 가입·비밀번호 찾기)을 쓸 수 없습니다.");
        } else if (pepper.length < MIN_PEPPER_LENGTH) {
            log.warn("[IDENTITY] IDENTITY_HASH_PEPPER 가 {}바이트로 짧습니다. openssl rand -base64 48 로 만든 값을 쓰세요.",
                    pepper.length);
        }
    }

    public boolean isConfigured() {
        return pepper.length > 0;
    }

    /** @throws IllegalStateException pepper 가 없을 때 — 호출 전에 {@link #isConfigured()} 로 막는다 */
    public String hash(String ci) {
        if (!isConfigured()) {
            throw new IllegalStateException("IDENTITY_HASH_PEPPER 가 없어 판정 키를 만들 수 없습니다.");
        }
        return hash(pepper, ci);
    }

    /** 계산 자체. 테스트 픽스처가 같은 키를 만들 때도 쓴다. */
    public static String hash(byte[] pepper, String ci) {
        if (ci == null || ci.isBlank()) {
            throw new IllegalArgumentException("CI 가 없습니다.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(ci.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("판정 키를 만들 수 없습니다.", e);
        }
    }
}
