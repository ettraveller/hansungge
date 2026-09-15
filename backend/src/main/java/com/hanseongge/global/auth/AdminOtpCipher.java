package com.hanseongge.global.auth;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 관리자 OTP 키 암호화와 복구 코드 요약.
 *
 * <h2>왜 암호화하는가</h2>
 * OTP 키가 평문으로 DB 에 있으면 DB 백업 하나가 새는 순간 2단계 인증이 통째로 무의미해진다.
 * 비밀번호는 해시라 되돌릴 수 없지만, OTP 키는 서버가 매번 6자리를 계산해야 해서 해시로
 * 둘 수 없다. 그래서 되돌릴 수 있는 암호화(AES-256-GCM)로 두고 키는 DB 밖에 둔다.
 *
 * <h2>키는 어디서 오는가</h2>
 * {@code ADMIN_OTP_ENCRYPTION_KEY}(base64, 32바이트 이상)가 있으면 그것을 쓴다. 없으면
 * JWT 서명키에서 파생한다 — 운영 서버에 새 환경변수를 넣지 않아도 배포가 깨지지 않게
 * 하려는 것이다. 대신 <b>JWT_SECRET 을 바꾸면 등록된 OTP 를 전부 초기화해야 한다.</b>
 *
 * <p>하나의 원본 키에서 용도별 키를 따로 뽑는다(암호화용 / 복구 코드 요약용). 같은 키를
 * 두 용도에 쓰면 한쪽의 약점이 다른 쪽으로 번진다.
 */
@Slf4j
@Component
public class AdminOtpCipher {

    private static final String FORMAT_PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec encryptionKey;
    private final byte[] digestKey;
    private final SecureRandom random = new SecureRandom();

    public AdminOtpCipher(AppProperties appProperties, JwtSigningKey signingKey) {
        byte[] master = resolveMaster(appProperties.adminSecurity().otpEncryptionKey(), signingKey);
        this.encryptionKey = new SecretKeySpec(hmac(master, "hanseongge/admin-otp/secret-encryption"), "AES");
        this.digestKey = hmac(master, "hanseongge/admin-otp/recovery-code-digest");
    }

    private static byte[] resolveMaster(String configured, JwtSigningKey signingKey) {
        if (configured != null && !configured.isBlank()) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(configured.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("ADMIN_OTP_ENCRYPTION_KEY 는 base64 여야 합니다.", e);
            }
            if (decoded.length < MIN_KEY_BYTES) {
                throw new IllegalStateException(
                        "ADMIN_OTP_ENCRYPTION_KEY 가 너무 짧습니다. 32바이트 이상이어야 합니다 (openssl rand -base64 32).");
            }
            return decoded;
        }
        log.info("[ADMIN-OTP] ADMIN_OTP_ENCRYPTION_KEY 가 없어 JWT 서명키에서 OTP 암호화 키를 파생합니다. "
                + "JWT_SECRET 을 바꾸면 등록된 OTP 를 전부 초기화해야 합니다.");
        return signingKey.key().getEncoded();
    }

    /** 평문 키 → {@code v1:base64(iv || 암호문 || 태그)} */
    public String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain);
            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OTP 키를 암호화하지 못했습니다", e);
        }
    }

    /**
     * 암호문 → 평문 키.
     *
     * <p>실패하면 조용히 넘기지 않는다. 암호화 키가 바뀌었다는 뜻이라 그 계정은 OTP 를
     * 초기화하는 것 말고 방법이 없고, 그 사실이 로그에 분명히 남아야 한다.
     */
    public byte[] decrypt(String stored) {
        if (stored == null || !stored.startsWith(FORMAT_PREFIX)) {
            throw new IllegalStateException("알 수 없는 OTP 키 형식입니다");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(FORMAT_PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            return cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("[ADMIN-OTP] OTP 키를 복호화하지 못했습니다. 암호화 키(ADMIN_OTP_ENCRYPTION_KEY 또는 JWT_SECRET)가 "
                    + "바뀌었을 수 있습니다. 해당 관리자의 OTP 를 초기화해야 합니다.");
            throw new IllegalStateException("OTP 키를 복호화하지 못했습니다", e);
        }
    }

    /** 복구 코드 요약값 (HMAC-SHA256, 16진수 64자). 코드는 정규화된 값을 넘긴다. */
    public String digest(String normalizedCode) {
        return HexFormat.of().formatHex(hmac(digestKey, normalizedCode));
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 을 쓸 수 없습니다", e);
        }
    }
}
