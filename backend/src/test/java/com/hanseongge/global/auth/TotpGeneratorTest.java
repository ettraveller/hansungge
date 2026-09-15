package com.hanseongge.global.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFC 6238 부록 B 의 SHA1 시험값으로 계산을 고정한다. 이 값이 어긋나면 인증 앱의 숫자와
 * 서버의 숫자가 달라 아무도 로그인하지 못한다.
 */
class TotpGeneratorTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
            "59,          287082",
            "1111111109,  081804",
            "1111111111,  050471",
            "1234567890,  005924",
            "2000000000,  279037",
            "20000000000, 353130"
    })
    void rfc6238Vectors(long epochSeconds, String expected) {
        assertThat(TotpGenerator.code(RFC_SECRET, TotpGenerator.stepAt(epochSeconds))).isEqualTo(expected);
    }

    @Test
    void base32RoundTrip() {
        assertThat(TotpGenerator.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpGenerator.fromBase32("gezd gnbv gy3t qojq GEZDGNBVGY3TQOJQ==")).isEqualTo(RFC_SECRET);

        byte[] random = TotpGenerator.newSecret();
        assertThat(TotpGenerator.fromBase32(TotpGenerator.base32(random))).isEqualTo(random);
    }

    @Test
    void fromBase32RejectsForeignCharacters() {
        assertThatThrownBy(() -> TotpGenerator.fromBase32("ABC1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchAllowsOneStepEitherSideAndReportsWhich() {
        long step = TotpGenerator.stepAt(1111111111);

        assertThat(TotpGenerator.match(RFC_SECRET, TotpGenerator.code(RFC_SECRET, step), step, 1))
                .isEqualTo(OptionalLong.of(step));
        assertThat(TotpGenerator.match(RFC_SECRET, TotpGenerator.code(RFC_SECRET, step - 1), step, 1))
                .isEqualTo(OptionalLong.of(step - 1));
        assertThat(TotpGenerator.match(RFC_SECRET, TotpGenerator.code(RFC_SECRET, step + 1), step, 1))
                .isEqualTo(OptionalLong.of(step + 1));
        assertThat(TotpGenerator.match(RFC_SECRET, TotpGenerator.code(RFC_SECRET, step - 2), step, 1))
                .isEmpty();
    }

    @Test
    void matchRejectsMalformedCodes() {
        long step = TotpGenerator.stepAt(59);
        assertThat(TotpGenerator.match(RFC_SECRET, null, step, 1)).isEmpty();
        assertThat(TotpGenerator.match(RFC_SECRET, "28708", step, 1)).isEmpty();
        assertThat(TotpGenerator.match(RFC_SECRET, "2870821", step, 1)).isEmpty();
        assertThat(TotpGenerator.match(RFC_SECRET, "28708a", step, 1)).isEmpty();
    }
}
