package com.hanseongge.global.util;

import com.hanseongge.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PhoneNumber — 로그인 ID 인 휴대폰번호")
class PhoneNumberTest {

    @Nested
    @DisplayName("같은 번호는 모양이 달라도 같은 값이 된다")
    class SameNumber {

        @ParameterizedTest
        @ValueSource(strings = {
                "01012345678",
                "010-1234-5678",
                "010 1234 5678",
                " 010.1234.5678 ",
                "+82 10-1234-5678",
                "+821012345678",
                "82-10-1234-5678"
        })
        void 전부_숫자만_남은_하나의_값(String raw) {
            assertThat(PhoneNumber.normalize(raw)).isEqualTo("01012345678");
        }

        @Test
        void 옛_국번과_열자리_번호도_받는다() {
            assertThat(PhoneNumber.normalize("011-123-4567")).isEqualTo("0111234567");
            assertThat(PhoneNumber.normalize("016-1234-5678")).isEqualTo("01612345678");
        }
    }

    @Nested
    @DisplayName("휴대폰번호가 아니면 받지 않는다")
    class Rejected {

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                "02-123-4567",        // 유선 — SMS 를 못 받는다
                "031-123-4567",
                "012-1234-5678",      // 없는 국번
                "010-123-456",        // 짧다
                "010-12345-67890",    // 길다
                "abc",
                "1012345678"          // 앞자리 0 이 빠졌다
        })
        void null_을_돌려준다(String raw) {
            assertThat(PhoneNumber.normalizeOrNull(raw)).isNull();
            assertThat(PhoneNumber.isValid(raw)).isFalse();
        }

        @Test
        void null_입력도_null() {
            assertThat(PhoneNumber.normalizeOrNull(null)).isNull();
        }

        @Test
        void normalize_는_입력_오류로_바꾼다() {
            assertThatThrownBy(() -> PhoneNumber.normalize("02-123-4567"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("결과는 DB 제약(숫자 10~11자리)을 항상 만족한다")
    void DB_제약과_같은_모양() {
        String normalized = PhoneNumber.normalize("+82 10-9876-5432");
        assertThat(normalized).matches("^[0-9]{10,11}$");
    }
}
