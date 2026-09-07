package com.hanseongge.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개인정보 가리기.
 *
 * <p>이 클래스가 하는 일은 하나다 — <b>원본이 밖으로 나가지 않게</b> 하는 것. 그래서
 * 이 테스트가 고정하는 것도 "예쁘게 가려지는가"가 아니라 <b>어떤 입력에도 원본이
 * 그대로 나오지 않는가</b>이다.
 *
 * <p>실제로 두 곳이 열려 있었다(2026-09-07). 둘 다 <b>가려진 것처럼 보이는데 가려지지
 * 않은</b> 형태라, 화면을 봐도 코드를 읽어도 눈에 띄지 않았다.
 * <ul>
 *   <li>아이디가 두 글자 이하인 이메일이 통째로 나갔다 — {@code me@company.com} 그대로</li>
 *   <li>전화번호의 별표 개수가 실제 가린 자릿수와 무관하게 항상 4개였다 —
 *       7자리 번호가 {@code 123-****-4567} 로 나와 한 자리도 가려지지 않았다</li>
 * </ul>
 */
class PrivacyMaskTest {

    @Nested
    @DisplayName("이름")
    class Name {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "홍길동, 홍**",
                "김수,   김*",
                "박,     박*",
                "남궁민수, 남***",
        })
        @DisplayName("첫 글자만 남기고 나머지 글자 수만큼 가린다")
        void keepsOnlyFirstCharacter(String raw, String expected) {
            assertThat(PrivacyMask.name(raw)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("이름이 없는 회원은 '회원' 으로 나간다 — 빈 칸을 두면 표가 무너진다")
        void blankBecomesAnonymous(String raw) {
            assertThat(PrivacyMask.name(raw)).isEqualTo("회원");
        }

        @Test
        @DisplayName("null 도 마찬가지다")
        void nullBecomesAnonymous() {
            assertThat(PrivacyMask.name(null)).isEqualTo("회원");
        }
    }

    @Nested
    @DisplayName("이메일")
    class Email {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "hongildong@example.com, ho********@example.com",
                "abc@x.com,              ab*@x.com",
        })
        @DisplayName("아이디 앞 두 글자만 남기고 도메인은 그대로 둔다")
        void keepsTwoCharactersAndDomain(String raw, String expected) {
            assertThat(PrivacyMask.email(raw)).isEqualTo(expected);
        }

        /**
         * 실제로 열려 있던 자리다.
         *
         * <p>"앞 두 글자를 남긴다"를 그대로 적용하면 아이디가 두 글자일 때 가릴 것이
         * 남지 않아 주소 전체가 나갔다.
         */
        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "me@company.com, **@company.com",
                "a@b.com,        *@b.com",
        })
        @DisplayName("아이디가 두 글자 이하면 통째로 가린다 — 남길 수 없으면 남기지 않는다")
        void shortLocalPartIsFullyMasked(String raw, String expected) {
            assertThat(PrivacyMask.email(raw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("어떤 이메일도 원본 그대로 나가지 않는다")
        void neverReturnsInput() {
            for (String raw : new String[]{
                    "a@b.com", "me@company.com", "abc@x.com", "hongildong@example.com"}) {
                assertThat(PrivacyMask.email(raw))
                        .as("가려지지 않고 그대로 나간 값: %s", raw)
                        .isNotEqualTo(raw);
            }
        }

        @Test
        @DisplayName("@ 가 없으면 이메일이 아니다 — 부분 노출 없이 통째로 가린다")
        void nonEmailIsFullyMasked() {
            assertThat(PrivacyMask.email("notanemail")).isEqualTo("**********");
        }

        @Test
        @DisplayName("값이 없으면 '-'")
        void blankBecomesDash() {
            assertThat(PrivacyMask.email(null)).isEqualTo("-");
            assertThat(PrivacyMask.email("  ")).isEqualTo("-");
        }
    }

    @Nested
    @DisplayName("휴대폰")
    class Phone {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "010-1234-5678, 010-****-5678",
                "01012345678,   010-****-5678",
                "010 1234 5678, 010-****-5678",
        })
        @DisplayName("11자리는 앞 3자리와 뒤 4자리를 남기고 가운데 4자리를 가린다")
        void masksMiddleOfElevenDigits(String raw, String expected) {
            assertThat(PrivacyMask.phone(raw)).isEqualTo(expected);
        }

        /**
         * 국가번호가 붙은 형태는 자릿수가 달라 앞 3자리가 {@code 821} 이 된다.
         *
         * <p>보기 좋지는 않지만 <b>안전한 방향으로 틀린다</b> — 가리는 자리는 오히려
         * 늘어난다. 실제로 이 값은 본인확인 응답에서 국내 형식으로 정규화되어 들어오므로
         * 화면에서 볼 일이 없고, 그래서 여기서 형식을 교정하지 않는다. 이 함수가 하는
         * 일은 예쁘게 만드는 것이 아니라 가리는 것이다.
         */
        @Test
        @DisplayName("국가번호가 붙어도 가려지는 자리는 줄지 않는다")
        void internationalFormatStillMasks() {
            assertThat(PrivacyMask.phone("+82 10 1234 5678")).isEqualTo("821-*****-5678");
        }

        /**
         * 실제로 열려 있던 자리다.
         *
         * <p>앞 3 + 뒤 4 를 남기면서 별표를 항상 4개 찍으면, 7자리 번호는 한 자리도
         * 가려지지 않은 채 {@code 123-****-4567} 로 나갔다. 별표가 거짓말을 한 것이다.
         */
        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "1234567,    ***4567",
                "021234567,  *****4567",
                "0212345678, ******5678",
        })
        @DisplayName("가릴 자리가 4개에 못 미치면 앞자리를 포기하고 뒤 4자리만 남긴다")
        void shortNumbersDropTheHead(String raw, String expected) {
            assertThat(PrivacyMask.phone(raw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("어떤 번호도 숫자가 전부 드러나지 않는다")
        void alwaysHidesSomeDigits() {
            for (String raw : new String[]{
                    "1234567", "12345678", "021234567", "0212345678", "01012345678"}) {
                String masked = PrivacyMask.phone(raw);
                String digits = raw.replaceAll("[^0-9]", "");
                String maskedDigits = masked.replaceAll("[^0-9]", "");

                assertThat(maskedDigits.length())
                        .as("가려진 자리가 없다: %s → %s", raw, masked)
                        .isLessThan(digits.length());
            }
        }

        @Test
        @DisplayName("별표 개수가 실제로 가린 자릿수와 같다 — 별표가 거짓말하지 않는다")
        void asteriskCountMatchesHiddenDigits() {
            for (String raw : new String[]{
                    "1234567", "12345678", "021234567", "0212345678", "01012345678"}) {
                String masked = PrivacyMask.phone(raw);
                long asterisks = masked.chars().filter(c -> c == '*').count();
                long shown = masked.chars().filter(Character::isDigit).count();

                assertThat(asterisks + shown)
                        .as("별표 %d + 노출 %d 가 원본 자릿수와 다르다: %s → %s",
                                asterisks, shown, raw, masked)
                        .isEqualTo(raw.replaceAll("[^0-9]", "").length());
            }
        }

        @Test
        @DisplayName("네 자리 이하는 통째로 가린다")
        void veryShortIsFullyMasked() {
            assertThat(PrivacyMask.phone("1234")).isEqualTo("****");
            assertThat(PrivacyMask.phone("12")).isEqualTo("**");
        }

        @Test
        @DisplayName("값이 없으면 '-'")
        void blankBecomesDash() {
            assertThat(PrivacyMask.phone(null)).isEqualTo("-");
            assertThat(PrivacyMask.phone("")).isEqualTo("-");
        }
    }
}
