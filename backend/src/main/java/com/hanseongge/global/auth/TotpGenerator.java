package com.hanseongge.global.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * TOTP (RFC 6238) — 휴대폰 인증 앱이 30초마다 바꾸는 6자리.
 *
 * <p>Google Authenticator·Microsoft Authenticator 가 쓰는 기본값(SHA1, 6자리, 30초)을 따른다.
 * 값을 바꾸면 일부 앱에서 등록은 되는데 숫자가 맞지 않는다.
 *
 * <h2>라이브러리를 쓰지 않는 이유</h2>
 * 계산은 HMAC 한 번과 자르기뿐이다(RFC 본문 20줄). 의존성을 더하면 그 라이브러리의
 * 기본값(구간 허용 폭, 재사용 처리)을 따로 확인해야 하고, 그 확인이 코드보다 길다.
 * RFC 부록의 시험값으로 {@code TotpGeneratorTest} 가 고정한다.
 */
public final class TotpGenerator {

    public static final int DIGITS = 6;
    public static final long PERIOD_SECONDS = 30;

    /** RFC 4226 이 권하는 160비트. 인증 앱이 받아들이는 가장 흔한 길이다. */
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpGenerator() {
    }

    public static byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static long stepAt(long epochSeconds) {
        return Math.floorDiv(epochSeconds, PERIOD_SECONDS);
    }

    public static long currentStep() {
        return stepAt(Instant.now().getEpochSecond());
    }

    /** 그 구간의 6자리. */
    public static String code(byte[] secret, long step) {
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 을 쓸 수 없습니다", e);
        }
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
    }

    /**
     * 입력한 6자리가 {@code step} 앞뒤 {@code window} 구간 중 어디와 맞는가.
     *
     * <p>앞뒤 한 구간을 허용하는 이유: 휴대폰 시계는 몇 초씩 어긋나고, 사람이 숫자를 읽고
     * 누르는 사이 구간이 넘어간다. 더 넓히면 맞힐 확률만 커진다.
     *
     * @return 맞은 구간. 재사용 여부는 호출부가 이 값으로 판단한다
     */
    public static OptionalLong match(byte[] secret, String code, long step, int window) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] input = code.getBytes(StandardCharsets.US_ASCII);
        for (long s = step - window; s <= step + window; s += 1) {
            // 시간차로 몇 자리가 맞았는지 새어 나가지 않게 고정 시간 비교를 쓴다.
            if (MessageDigest.isEqual(code(secret, s).getBytes(StandardCharsets.US_ASCII), input)) {
                return OptionalLong.of(s);
            }
        }
        return OptionalLong.empty();
    }

    /** 인증 앱에 넣는 키 표기 (RFC 4648 Base32, 채움 문자 없음). */
    public static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    /** Base32 를 되돌린다. 공백·소문자·채움 문자는 무시한다 — 사람이 옮겨 적은 값도 받는다. */
    public static byte[] fromBase32(String text) {
        String cleaned = text.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : cleaned.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Base32 가 아닌 문자: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        return out.toByteArray();
    }
}
