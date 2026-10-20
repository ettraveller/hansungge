package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
import com.hanseongge.support.StubMailConfig;
import com.hanseongge.support.StubMailSender;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자체(휴대폰) 가입과 휴대폰번호 로그인.
 *
 * <p>검증하려는 것은 KG이니시스의 응답 형식이 아니라 <b>우리 쪽 흐름</b>이다 — 단계를 건너뛸
 * 수 있는가, 같은 사람이 두 계정을 만들 수 있는가, 번호가 재할당되면 어떻게 되는가,
 * 토큰에 개인정보가 새어 나가는가, 잠금이 스스로 풀리는가.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback",
        // 재발송 쿨다운을 끈다 — 한 테스트에서 인증번호를 두 번 받는 경우가 있다.
        "app.sign-up.code-resend-cooldown-seconds=0"
})
class SelfSignUpIntegrationTest extends EmbeddedPostgresSupport {

    private static final String PASSWORD = SignUpFlow.PASSWORD;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;
    @Autowired StubIdentityVerificationProvider identityProvider;
    @Autowired StubMailSender mailSender;

    private SignUpFlow flow;

    @BeforeEach
    void setUp() {
        google.reset();
        identityProvider.reset();
        mailSender.reset();
        flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);
        UserTestFixtures.deleteAll(jdbc);
    }

    // =================================================================
    // 도우미
    // =================================================================

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 약관 → 본인확인까지. 신규면 SET_PASSWORD 와 진행 토큰이 온다. */
    private JsonNode agreeAndVerify(UserTestFixtures.Person person) throws Exception {
        String agreed = flow.agree();
        identityProvider.willReturn(person);
        return flow.verifyIdentity(agreed);
    }

    private MvcResult complete(String signUpToken, String password, String email, String emailCode)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signUpToken", signUpToken);
        body.put("password", password);
        if (email != null) {
            body.put("email", email);
        }
        if (emailCode != null) {
            body.put("emailCode", emailCode);
        }
        return mockMvc.perform(post("/api/v1/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult login(String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "password", password))))
                .andReturn();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    // =================================================================
    // 자체 가입
    // =================================================================

    @Nested
    @DisplayName("자체 가입 — 약관 → 본인확인 → 비밀번호 → 이메일(선택)")
    class SignUp {

        @Test
        @DisplayName("본인확인 값으로 가입된다 — 이름·생년월일·휴대폰을 입력받지 않는다")
        void signUpUsesIdentityValues() throws Exception {
            JsonNode verified = agreeAndVerify(
                    UserTestFixtures.personWith("CI-1", "홍길동", "010-1234-5678"));

            assertThat(verified.get("step").asText()).isEqualTo("SET_PASSWORD");
            // 비밀번호 화면에 읽기 전용으로 보여줄 본인 값
            assertThat(verified.get("name").asText()).isEqualTo("홍길동");
            assertThat(verified.get("phone").asText()).isEqualTo("01012345678");
            // 아직 계정은 없다
            assertThat(count("users")).isZero();

            MvcResult done = complete(verified.get("signUpToken").asText(), PASSWORD, null, null);
            assertThat(done.getResponse().getStatus()).isEqualTo(200);
            assertThat(read(done).get("accessToken").asText()).isNotBlank();

            Map<String, Object> user = jdbc.queryForMap(
                    "SELECT name, phone, identity_key, email, password_hash FROM users");
            assertThat(user.get("name")).isEqualTo("홍길동");
            assertThat(user.get("phone")).isEqualTo("01012345678");
            // CI 원본이 아니라 해시가 저장된다
            assertThat(user.get("identity_key")).isEqualTo(UserTestFixtures.keyOf("CI-1"));
            assertThat((String) user.get("identity_key")).hasSize(64).doesNotContain("CI-1");
            assertThat(user.get("email")).isNull();
            assertThat((String) user.get("password_hash")).startsWith("$2");
        }

        @Test
        @DisplayName("가입한 번호와 비밀번호로 로그인된다 — 하이픈이 있어도 같은 번호다")
        void signedUpMemberLogsInByPhone() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-1", "홍길동", "01012345678"), PASSWORD);

            MvcResult result = login("010-1234-5678", PASSWORD);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(read(result).get("user").get("phone").asText()).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("이메일을 넣으면 인증번호를 확인해야 가입된다")
        void emailNeedsCode() throws Exception {
            String token = agreeAndVerify(UserTestFixtures.person("홍길동")).get("signUpToken").asText();

            mockMvc.perform(post("/api/v1/auth/email/verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("signUpToken", token, "email", "Buyer@Test.local"))))
                    .andExpect(status().isNoContent());

            // 번호 없이는 안 된다
            assertThat(complete(token, PASSWORD, "buyer@test.local", null).getResponse().getStatus())
                    .isEqualTo(400);
            // 틀린 번호도 안 된다
            assertThat(complete(token, PASSWORD, "buyer@test.local", "000000").getResponse().getStatus())
                    .isEqualTo(400);

            MvcResult ok = complete(token, PASSWORD, "buyer@test.local", mailSender.lastCode());
            assertThat(ok.getResponse().getStatus()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT email FROM users", String.class))
                    .isEqualTo("buyer@test.local");
            assertThat(jdbc.queryForObject(
                    "SELECT email_verified_at IS NOT NULL FROM users", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("본인확인을 건너뛰고 가입을 마칠 수 없다")
        void identityCannotBeSkipped() throws Exception {
            String agreedOnly = flow.agree();

            MvcResult result = complete(agreedOnly, PASSWORD, null, null);
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(result).get("code").asText()).isEqualTo("SIGNUP_TOKEN_INVALID");
            assertThat(count("users")).isZero();
        }

        @Test
        @DisplayName("만 14세 미만은 본인확인 단계에서 막힌다 — 신원을 저장하지도 않는다")
        void underFourteenIsBlocked() throws Exception {
            String agreed = flow.agree();
            identityProvider.willReturn(UserTestFixtures.minor(LocalDate.now()));

            MvcResult result = flow.verifyIdentityRaw(agreed);
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(read(result).get("code").asText()).isEqualTo("UNDER_MINIMUM_AGE");
            assertThat(count("identity_verifications")).isZero();
        }

        @Test
        @DisplayName("CI 를 주지 않는 인증 수단이면 다른 수단을 고르라고 한다 — 아무것도 저장하지 않는다")
        void missingCiIsRejected() throws Exception {
            String agreed = flow.agree();
            identityProvider.omitCi = true;

            MvcResult result = flow.verifyIdentityRaw(agreed);
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(result).get("code").asText()).isEqualTo("IDENTITY_KEY_UNAVAILABLE");
            assertThat(count("identity_verifications")).isZero();
        }

        @Test
        @DisplayName("비밀번호 정책에 맞지 않으면 400")
        void weakPasswordIsRejected() throws Exception {
            String token = agreeAndVerify(UserTestFixtures.person("홍길동")).get("signUpToken").asText();
            assertThat(complete(token, "short", null, null).getResponse().getStatus()).isEqualTo(400);
        }
    }

    // =================================================================
    // 이미 가입한 사람
    // =================================================================

    @Nested
    @DisplayName("같은 사람(같은 CI)은 휴대폰 계정을 두 개 만들 수 없다")
    class Duplicate {

        @Test
        @DisplayName("다시 가입하면 EXISTING_ACCOUNT — 가린 정보와 재설정 토큰을 주고, 계정은 늘지 않는다")
        void secondSignUpIsTurnedIntoExistingAccount() throws Exception {
            UserTestFixtures.Person person = UserTestFixtures.personWith("CI-SAME", "홍길동", "01012345678");
            flow.phone(person, PASSWORD);

            JsonNode again = agreeAndVerify(person);

            assertThat(again.get("step").asText()).isEqualTo("EXISTING_ACCOUNT");
            assertThat(again.get("accountPhone").asText()).isEqualTo("010-****-5678");
            assertThat(again.get("accountHasPassword").asBoolean()).isTrue();
            assertThat(again.get("resetToken").asText()).isNotBlank();
            assertThat(count("users")).isEqualTo(1);
        }


        @Test
        @DisplayName("DI 가 오지 않아도 CI 로 알아본다 — 간편인증으로 가입한 사람이 문자인증으로 다시 와도 한 계정")
        void ciCatchesSamePersonWhenDiMissing() throws Exception {
            UserTestFixtures.Person person = UserTestFixtures.personWith("CI-ONLY", "홍길동", "01012345678");
            // 첫 가입은 DI 를 주지 않는 수단으로.
            identityProvider.omitDi = true;
            flow.phone(person, PASSWORD);

            // 두 번째는 DI 까지 주는 수단으로 돌아온다.
            identityProvider.omitDi = false;
            JsonNode again = agreeAndVerify(person);

            assertThat(again.get("step").asText()).isEqualTo("EXISTING_ACCOUNT");
            assertThat(count("users")).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 사람은 DI 가 달라 그대로 새 계정이 된다")
        void differentPersonStillSignsUp() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-A", "홍길동", "01011112222"), PASSWORD);

            JsonNode other = agreeAndVerify(UserTestFixtures.personWith("CI-B", "김철수", "01033334444"));

            assertThat(other.get("step").asText()).isEqualTo("SET_PASSWORD");
        }

        @Test
        @DisplayName("그 자리에서 바로 비밀번호를 다시 정할 수 있다 — 본인확인을 이미 통과했다")
        void existingAccountCanResetRightAway() throws Exception {
            UserTestFixtures.Person person = UserTestFixtures.personWith("CI-SAME", "홍길동", "01012345678");
            flow.phone(person, PASSWORD);
            String resetToken = agreeAndVerify(person).get("resetToken").asText();

            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("resetToken", resetToken, "newPassword", "Changed-pass-99!"))))
                    .andExpect(status().isNoContent());

            assertThat(login("01012345678", "Changed-pass-99!").getResponse().getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("소셜로 가입해 본인인증까지 한 사람이 휴대폰 가입을 하면 그 소셜 계정으로 안내한다 — 계정이 늘지 않는다")
        void socialMemberWithIdentityIsExisting() throws Exception {
            UserTestFixtures.Person person = UserTestFixtures.personWith("CI-SAME", "홍길동", "01012345678");
            String token = flow.social("google-1", "buyer@test.local", "구글닉네임");
            flow.verifyIdentityAsMember(token, person);

            JsonNode again = agreeAndVerify(person);

            assertThat(again.get("step").asText()).isEqualTo("EXISTING_ACCOUNT");
            assertThat(again.get("accountHasPassword").asBoolean()).isFalse();
            assertThat(count("users")).isEqualTo(1);

            // 그 자리에서 비밀번호를 정하면 휴대폰 로그인이 된다
            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("resetToken", again.get("resetToken").asText(),
                                    "newPassword", PASSWORD))))
                    .andExpect(status().isNoContent());
            assertThat(login("01012345678", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("본인인증을 받지 않은 소셜 회원은 같은 사람인지 모른다 — 휴대폰 가입이 따로 된다 (중복가입 허용)")
        void unverifiedSocialMemberDoesNotBlockPhoneSignUp() throws Exception {
            flow.social("google-1", "buyer@test.local", "홍길동");

            flow.phone(UserTestFixtures.person("홍길동"), PASSWORD);

            assertThat(count("users")).isEqualTo(2);
        }

        @Test
        @DisplayName("번호를 바꾼 사람이 다시 본인확인하면 로그인 번호가 새 번호로 맞춰진다")
        void changedNumberIsRefreshed() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-SAME", "홍길동", "01011111111"), PASSWORD);

            agreeAndVerify(UserTestFixtures.personWith("CI-SAME", "홍길동", "01022222222"));

            assertThat(jdbc.queryForObject("SELECT phone FROM users", String.class)).isEqualTo("01022222222");
            assertThat(login("01022222222", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }
    }

    // =================================================================
    // 번호 재할당
    // =================================================================

    @Nested
    @DisplayName("번호가 다른 사람에게 재할당되면")
    class Reassigned {

        @Test
        @DisplayName("새 주인은 가입되고, 옛 계정은 로그인 번호를 잃는다 — 계정이 합쳐지지 않는다")
        void newOwnerSignsUpAndOldAccountLosesNumber() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-OLD", "김옛주인", "01012345678"), PASSWORD);

            flow.phone(UserTestFixtures.personWith("CI-NEW", "박새주인", "01012345678"), "New-owner-pass-1!");

            assertThat(count("users")).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE identity_key = ?", String.class,
                    UserTestFixtures.keyOf("CI-OLD"))).isNull();
            assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE identity_key = ?", String.class,
                    UserTestFixtures.keyOf("CI-NEW"))).isEqualTo("01012345678");

            // 그 번호로 로그인하면 새 주인이다
            MvcResult result = login("01012345678", "New-owner-pass-1!");
            assertThat(read(result).get("user").get("name").asText()).isEqualTo("박새주인");
            // 옛 비밀번호로는 들어갈 수 없다
            assertThat(login("01012345678", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        }
    }

    // =================================================================
    // 휴대폰 로그인
    // =================================================================

    @Nested
    @DisplayName("휴대폰번호 로그인")
    class PasswordLogin {

        @Test
        @DisplayName("없는 번호와 틀린 비밀번호는 같은 응답이다 — 가입 여부를 흘리지 않는다")
        void unknownAndWrongLookTheSame() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-1", "홍길동", "01012345678"), PASSWORD);

            MvcResult unknown = login("01099999999", PASSWORD);
            MvcResult wrong = login("01012345678", "Wrong-pass-1234!");
            MvcResult malformed = login("02-123-4567", PASSWORD);

            assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
            assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
            assertThat(malformed.getResponse().getStatus()).isEqualTo(401);
            assertThat(read(unknown).get("code").asText()).isEqualTo("LOGIN_FAILED");
            assertThat(read(wrong).get("code").asText()).isEqualTo("LOGIN_FAILED");
            assertThat(read(malformed).get("code").asText()).isEqualTo("LOGIN_FAILED");
        }

        @Test
        @DisplayName("본인인증으로 번호는 있지만 비밀번호가 없는 소셜 회원에게는 그 사실을 알려준다")
        void socialOnlyMemberIsTold() throws Exception {
            String token = flow.social("google-1", "buyer@test.local", "홍길동");
            flow.verifyIdentityAsMember(token, UserTestFixtures.personWith("CI-1", "홍길동", "01012345678"));

            MvcResult result = login("01012345678", PASSWORD);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(result).get("code").asText()).isEqualTo("PASSWORD_NOT_SET");
        }

        @Test
        @DisplayName("5번 틀리면 잠기고, 맞는 비밀번호도 통하지 않는다")
        void locksAfterFiveFailures() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-1", "홍길동", "01012345678"), PASSWORD);

            for (int i = 0; i < 5; i++) {
                login("01012345678", "Wrong-pass-1234!");
            }
            MvcResult locked = login("01012345678", PASSWORD);
            assertThat(locked.getResponse().getStatus()).isEqualTo(403);
            assertThat(read(locked).get("code").asText()).isEqualTo("USER_ACCOUNT_LOCKED");
        }

        @Test
        @DisplayName("30분이 지나 풀린 뒤 한 번 틀려도 곧바로 다시 잠기지 않는다 — 실패 횟수를 새로 센다")
        void expiredLockStartsCountingAgain() throws Exception {
            flow.phone(UserTestFixtures.personWith("CI-1", "홍길동", "01012345678"), PASSWORD);
            for (int i = 0; i < 5; i++) {
                login("01012345678", "Wrong-pass-1234!");
            }
            // 잠긴 지 31분이 지났다
            jdbc.update("UPDATE users SET locked_at = now() - interval '31 minutes'");

            MvcResult oneMoreWrong = login("01012345678", "Wrong-pass-1234!");
            assertThat(read(oneMoreWrong).get("code").asText())
                    .as("풀린 뒤 첫 실패는 그냥 실패여야 한다")
                    .isEqualTo("LOGIN_FAILED");
            assertThat(jdbc.queryForObject("SELECT login_fail_count FROM users", Integer.class)).isEqualTo(1);

            assertThat(login("01012345678", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }
    }

    // =================================================================
    // 개인정보
    // =================================================================

    @Nested
    @DisplayName("개인정보 취급")
    class PrivacyGuards {

        @Test
        @DisplayName("진행 토큰을 디코딩해도 이름·생년월일·휴대폰·CI 가 없다 — JWT 는 암호화가 아니다")
        void signUpTokenCarriesNoPersonalData() throws Exception {
            JsonNode verified = agreeAndVerify(
                    UserTestFixtures.personWith("CI-SECRET-VALUE", "김철수", "01011112222"));

            String token = verified.get("signUpToken").asText();
            String payload = new String(java.util.Base64.getUrlDecoder()
                    .decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);

            assertThat(payload).doesNotContain("CI-SECRET-VALUE");
            assertThat(payload).doesNotContain(UserTestFixtures.keyOf("CI-SECRET-VALUE"));
            assertThat(payload).doesNotContain("김철수");
            assertThat(payload).doesNotContain("01011112222");
            assertThat(payload).doesNotContain("1990");
        }

        @Test
        @DisplayName("CI 원본을 담을 자리가 아예 없다 — 해시(identity_key)만 있다")
        void thereIsNoPlaceForCi() {
            Integer columns = jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = 'public' AND lower(column_name) = 'ci'
                    """, Integer.class);
            assertThat(columns).isZero();
        }

        @Test
        @DisplayName("본인확인 신원은 고칠 수도 지울 수도 없다")
        void identityRowIsAppendOnly() throws Exception {
            flow.phone(UserTestFixtures.person("홍길동"), PASSWORD);
            Long id = jdbc.queryForObject("SELECT id FROM identity_verifications LIMIT 1", Long.class);

            assertThatThrownBySql(() -> jdbc.update(
                    "UPDATE identity_verifications SET name = '남의이름' WHERE id = ?", id));
            assertThatThrownBySql(() -> jdbc.update(
                    "DELETE FROM identity_verifications WHERE id = ?", id));
        }

        @Test
        @DisplayName("한 번 쓴 본인확인은 다시 쓸 수 없다 — 가입에 쓴 인증으로 계정을 또 만들지 못한다")
        void consumedIdentityCannotBeReused() throws Exception {
            JsonNode verified = agreeAndVerify(UserTestFixtures.person("홍길동"));
            String token = verified.get("signUpToken").asText();
            assertThat(complete(token, PASSWORD, null, null).getResponse().getStatus()).isEqualTo(200);

            MvcResult again = complete(token, PASSWORD, null, null);
            assertThat(again.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
            assertThat(count("users")).isEqualTo(1);
        }

        private void assertThatThrownBySql(Runnable action) {
            org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("append-only");
        }
    }
}
