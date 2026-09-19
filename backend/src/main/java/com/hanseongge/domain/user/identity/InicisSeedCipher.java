package com.hanseongge.domain.user.identity;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SEEDEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * KG이니시스 통합인증 결과 복호화 — SEED-CBC.
 *
 * <p>인증 요청에 {@code reservedMsg=isUseToken=Y} 를 넣으면, 결과조회 응답의 개인정보
 * (이름·전화·생년월일·DI 등)가 SEED 로 암호화돼 온다. 규칙은 이니시스 공식 샘플
 * ({@code SaSample/JSP/src/sa/SEED_ENC.java})과 같다.
 *
 * <ul>
 *   <li>키: 인증 응답으로 받은 {@code token} 을 Base64 로 푼 16바이트
 *   <li>IV: 고정 문자열 {@code SASKGINICIS00000}
 *   <li>패딩: PKCS#7 (KISA SEED-CBC 구현과 같다)
 *   <li>평문: UTF-8
 * </ul>
 *
 * <p>호환성은 KISA 공식 코드로 만든 암호문으로 단위테스트가 고정한다.
 */
final class InicisSeedCipher {

    private static final byte[] IV = "SASKGINICIS00000".getBytes(StandardCharsets.US_ASCII);

    private InicisSeedCipher() {
    }

    /**
     * @param base64CipherText 결과조회 응답의 암호화된 값
     * @param token            인증 응답(STEP2)의 {@code token}
     * @return 평문. 값이 비어 있으면 null
     * @throws IdentityVerificationException 키·암호문이 올바르지 않으면
     */
    static String decrypt(String base64CipherText, String token) {
        if (base64CipherText == null || base64CipherText.isBlank()) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(token);
            byte[] cipherText = Base64.getDecoder().decode(base64CipherText);

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    CBCBlockCipher.newInstance(new SEEDEngine()), new PKCS7Padding());
            cipher.init(false, new ParametersWithIV(new KeyParameter(key), IV));

            byte[] out = new byte[cipher.getOutputSize(cipherText.length)];
            int length = cipher.processBytes(cipherText, 0, cipherText.length, out, 0);
            length += cipher.doFinal(out, length);
            return new String(Arrays.copyOf(out, length), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | InvalidCipherTextException e) {
            // 평문 조각이 섞일 수 있어 원인 메시지만 남긴다.
            throw new IdentityVerificationException("이니시스 결과 복호화 실패: " + e.getClass().getSimpleName(), e);
        }
    }
}
