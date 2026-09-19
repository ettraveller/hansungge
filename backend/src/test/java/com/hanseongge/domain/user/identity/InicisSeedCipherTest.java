package com.hanseongge.domain.user.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이니시스 결과 복호화 호환성.
 *
 * <p>아래 암호문은 <b>이니시스 공식 샘플에 들어 있는 KISA SEED-CBC 코드</b>
 * ({@code SaSample.zip} → {@code KISA_SEED_CBC.SEED_CBC_Encrypt})로 만든 것이다. 같은 키·IV 로
 * 샘플의 {@code SEED_ENC.decrypt} 가 전부 원문으로 되돌렸다. 이 값을 BouncyCastle 구현이
 * 똑같이 풀어야 실제 이니시스 응답도 풀린다.
 *
 * <p>키는 이니시스가 인증 응답으로 주는 {@code token} 과 같은 모양(16바이트를 Base64 로)이다.
 */
@DisplayName("InicisSeedCipher — KISA 공식 SEED-CBC 와 호환")
class InicisSeedCipherTest {

    private static final String TOKEN = "AxQlNkdYaXqLnK2+z+DxAg==";

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '\t', value = {
            "홍길동\tkP26HGm9gIQmVaSPFkoqpg==",
            "19900101\tBa+BWjYbw7g+UX8sClAlCQ==",
            "M\txTmIYnGKwEt2Qqoo56O0nw==",
            "0\t2UwEe5Nh/JnsCziutOZDMQ==",
            "01012345678\tFzK9pU+A6qVIs5ElsEf2IA==",
            "ab\t9QvUms+HWoMQ38zd6yad/g==",
            // 정확히 16바이트 — 패딩 블록이 통째로 하나 더 붙는 경우
            "1234567890123456\tbD7v4qcn9DeqqOVNbGcyFf1xYARtRiOyN4D5Qas6/IE="
    })
    void KISA_코드로_만든_암호문을_그대로_푼다(String plain, String cipherText) {
        assertThat(InicisSeedCipher.decrypt(cipherText, TOKEN)).isEqualTo(plain);
    }

    @Test
    @DisplayName("DI 처럼 여러 블록에 걸친 긴 값")
    void 긴_값() {
        assertThat(InicisSeedCipher.decrypt(
                "q/QZurdhF7HO1e8LT011pt7OoecSEQk6aqmWIMF/JgnAd7nvmdF9KJLjY6Di3Pi70alvMJSnaLMypQWIZ7bJ3NWxbu2i4i+FDBFMQqrXWNQ=",
                TOKEN))
                .isEqualTo("MC0GCCqGSIb3DQIJAyEAbfDxaJ2d7gk8m1P7ZqNf0x5e9s2Q1rT3vY6uW8aB4cD=");
    }

    @Test
    @DisplayName("비어 있는 값은 null — 이니시스는 실패 시 필드를 보내지 않는다")
    void 빈_값() {
        assertThat(InicisSeedCipher.decrypt(null, TOKEN)).isNull();
        assertThat(InicisSeedCipher.decrypt("", TOKEN)).isNull();
    }

    @Test
    @DisplayName("다른 키로는 풀리지 않는다 — 조용히 쓰레기 값을 내지 않고 실패한다")
    void 다른_키() {
        String otherKey = "AAAAAAAAAAAAAAAAAAAAAA==";
        assertThatThrownBy(() -> InicisSeedCipher.decrypt("kP26HGm9gIQmVaSPFkoqpg==", otherKey))
                .isInstanceOf(IdentityVerificationException.class);
    }
}
