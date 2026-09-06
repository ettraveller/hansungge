package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.user.entity.Gender;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
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
 * 자체 회원가입과 휴대폰 본인확인.
 *
 * <p>실제 공급자 대신 스텁을 끼운다. 검증하려는 것은 KG이니시스의 응답 형식이 아니라
 * <b>우리 쪽 흐름</b>이다 — 단계를 건너뛸 수 있는가, 같은 사람이 두 계정을 만들 수 있는가,
 * 토큰에 개인정보가 새어 나가는가.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback",
        // 재발송 쿨다운을 끈다 — 한 테스트에서 인증번호를 두 번 받는 경우가 있다.
        "app.sign-up.email-code-resend-cooldown-seconds=0"
})
class SelfSignUpIntegrationTest extends EmbeddedPostgresSupport {

    private static final String PASSWORD = "Hanseong!2026";
    private static final String RETURN_URL = SignUpFlow.RETURN_URL;

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

    /** 이메일 인증까지 마치고 가입 토큰을 돌려준다. */
    private String verifyEmail(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isNoContent());

        MvcResult confirmed = mockMvc.perform(post("/api/v1/auth/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "code", mailSender.lastCode()))))
                .andExpect(status().isOk())
                .andReturn();
        return read(confirmed).get("signUpToken").asText();
    }

    private MvcResult completeIdentity(String signUpToken, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signUpToken", signUpToken);
        body.put("providerToken", "stub-token");
        if (password != null) {
            body.put("password", password);
        }
        return mockMvc.perform(post("/api/v1/auth/identity/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult signUp(String signUpToken) throws Exception {
        return signUp(signUpToken, null);
    }

    /**
     * 비밀번호를 여기서 다시 보낸다.
     *
     * <p>본인확인 단계에서도 보내지만 그것은 <b>병합용</b>이다 — 이미 가입한 사람이면
     * 그 자리에서 기존 계정에 붙여야 한다. 신규 가입이면 그 값을 서버가 들고 있지 않는다.
     * 단계 사이에 비밀번호를 서버에 보관하지 않기 위해서다.
     */
    private MvcResult signUp(String signUpToken, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signUpToken", signUpToken);
        body.put("agreements", SignUpFlow.allRequiredAgreed());
        if (password != null) {
            body.put("password", password);
        }
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /** 자체 가입을 끝까지 태운다. */
    private String signUpWithEmail(String email) throws Exception {
        String afterEmail = verifyEmail(email);
        MvcResult identity = completeIdentity(afterEmail, PASSWORD);
        assertThat(identity.getResponse().getStatus()).isEqualTo(200);
        MvcResult signedUp = signUp(read(identity).get("signUpToken").asText(), PASSWORD);
        assertThat(signedUp.getResponse().getStatus()).isEqualTo(200);
        return read(signedUp).get("accessToken").asText();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    // =================================================================
    // 자체 가입
    // =================================================================

    @Nested
    @DisplayName("자체 가입 — 이메일 인증 → 본인확인 → 약관")
    class EmailSignUp {

        @Test
        @DisplayName("네 단계를 마치면 회원이 생기고 세션이 발급된다")
        void completesInFourSteps() throws Exception {
            String token = signUpWithEmail("buyer@test.local");

            assertThat(token).isNotBlank();
            assertThat(count("users")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT password_hash FROM users LIMIT 1", String.class)).isNotBlank();
            assertThat(jdbc.queryForObject(
                    "SELECT di FROM users LIMIT 1", String.class)).isNotBlank();
            // 약관은 선택까지 포함해 전부 기록한다 — "묻지 않은 것"과 "거절한 것"은 다르다.
            assertThat(count("user_agreements")).isEqualTo(5);
        }

        @Test
        @DisplayName("인증번호가 틀리면 다음 단계로 갈 수 없다")
        void wrongCodeIsRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/email/verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local"))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/auth/email/verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local", "code", "000000"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        }

        @Test
        @DisplayName("시도 횟수를 넘기면 막힌다 — 6자리는 막지 않으면 만료 전에 다 훑을 수 있다")
        void tooManyAttemptsAreBlocked() throws Exception {
            mockMvc.perform(post("/api/v1/auth/email/verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local"))))
                    .andExpect(status().isNoContent());

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/auth/email/verification/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", "buyer@test.local", "code", "000000"))))
                        .andExpect(status().isBadRequest());
            }

            mockMvc.perform(post("/api/v1/auth/email/verification/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local", "code", "000000"))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED"));
        }

        @Test
        @DisplayName("본인확인을 건너뛰고 가입할 수 없다")
        void identityCannotBeSkipped() throws Exception {
            String afterEmail = verifyEmail("buyer@test.local");

            MvcResult result = signUp(afterEmail);

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(result).get("code").asText()).isEqualTo("IDENTITY_VERIFICATION_INVALID");
            assertThat(count("users")).isZero();
        }

        @Test
        @DisplayName("만 14세 미만은 가입할 수 없다 — 체크박스가 아니라 생년월일로 판정한다")
        void minorCannotSignUp() throws Exception {
            String afterEmail = verifyEmail("kid@test.local");
            identityProvider.willReturn(UserTestFixtures.minorIdentity(LocalDate.now()));

            MvcResult result = completeIdentity(afterEmail, PASSWORD);

            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(read(result).get("code").asText()).isEqualTo("UNDER_MINIMUM_AGE");
            assertThat(count("users")).isZero();
            // 가입시킬 수 없는 사람의 신원을 남기지 않는다.
            assertThat(count("identity_verifications")).isZero();
        }
    }

    // =================================================================
    // 중복가입과 계정 병합
    // =================================================================

    @Nested
    @DisplayName("DI 가 계정의 진짜 열쇠다")
    class IdentityIsTheKey {

        private VerifiedIdentity sameHuman() {
            return new VerifiedIdentity("DI-SAME-PERSON", "김철수",
                    LocalDate.of(1990, 5, 5), Gender.MALE, false, "010-1111-2222");
        }

        @Test
        @DisplayName("같은 DI 로 두 번째 계정을 만들 수 없다 — 이메일이 달라도 막힌다")
        void sameDiCannotCreateSecondAccount() throws Exception {
            identityProvider.willReturn(sameHuman());
            signUpWithEmail("first@test.local");
            assertThat(count("users")).isEqualTo(1);

            // 다른 이메일로 다시 가입을 시도한다.
            identityProvider.willReturn(sameHuman());
            String afterEmail = verifyEmail("second@test.local");
            MvcResult result = completeIdentity(afterEmail, PASSWORD);

            // 새 계정이 생기지 않는다. 이미 비밀번호가 있으므로 안내로 막힌다.
            assertThat(count("users")).isEqualTo(1);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
        }

        @Test
        @DisplayName("소셜로 가입한 사람이 같은 신원으로 자체 가입하면 기존 계정에 비밀번호가 붙는다")
        void socialUserGainsPassword() throws Exception {
            identityProvider.willReturn(sameHuman());
            flow.social("google-1", "buyer@test.local", "김철수", sameHuman());
            assertThat(count("users")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users LIMIT 1", String.class))
                    .isNull();

            identityProvider.willReturn(sameHuman());
            String afterEmail = verifyEmail("buyer@test.local");
            MvcResult result = completeIdentity(afterEmail, PASSWORD);

            // 계정이 갈라지지 않고 하나로 남는다.
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(read(result).hasNonNull("accessToken")).isTrue();
            assertThat(count("users")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users LIMIT 1", String.class))
                    .isNotBlank();
        }

        @Test
        @DisplayName("한 번 쓴 본인확인은 다시 쓸 수 없다")
        void consumedIdentityCannotBeReused() throws Exception {
            String afterEmail = verifyEmail("buyer@test.local");
            MvcResult identity = completeIdentity(afterEmail, PASSWORD);
            String tokenWithIdentity = read(identity).get("signUpToken").asText();

            assertThat(signUp(tokenWithIdentity, PASSWORD).getResponse().getStatus()).isEqualTo(200);

            // 같은 토큰으로 한 번 더 — 소진된 인증이라 거절된다.
            MvcResult second = signUp(tokenWithIdentity, PASSWORD);
            assertThat(second.getResponse().getStatus()).isEqualTo(400);
            assertThat(count("users")).isEqualTo(1);
        }
    }

    // =================================================================
    // 이메일 로그인
    // =================================================================

    @Nested
    @DisplayName("이메일 로그인")
    class PasswordSignIn {

        private MvcResult login(String email, String password) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", password))))
                    .andReturn();
        }

        @Test
        @DisplayName("가입한 비밀번호로 로그인된다")
        void signsIn() throws Exception {
            signUpWithEmail("buyer@test.local");

            MvcResult result = login("buyer@test.local", PASSWORD);

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(read(result).hasNonNull("accessToken")).isTrue();
            assertThat(result.getResponse().getCookie("hsg_rt")).isNotNull();
        }

        @Test
        @DisplayName("없는 계정과 틀린 비밀번호가 같은 응답이다 — 구분되면 가입 여부를 훑을 수 있다")
        void unknownAndWrongLookTheSame() throws Exception {
            signUpWithEmail("buyer@test.local");

            MvcResult wrong = login("buyer@test.local", "Wrong!Password9");
            MvcResult unknown = login("nobody@test.local", "Wrong!Password9");

            assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
            assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
            assertThat(read(wrong).get("code").asText())
                    .isEqualTo(read(unknown).get("code").asText());
        }

        @Test
        @DisplayName("소셜 전용 계정은 비밀번호가 없다고 알려준다 — 맞는 비밀번호를 계속 넣게 두지 않는다")
        void socialOnlyAccountIsToldSo() throws Exception {
            flow.social("google-1", "social@test.local", "김철수");

            MvcResult result = login("social@test.local", PASSWORD);

            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(result).get("code").asText()).isEqualTo("PASSWORD_NOT_SET");
        }

        @Test
        @DisplayName("연속 실패하면 잠기고, 실패 횟수가 롤백되지 않는다")
        void locksAfterRepeatedFailures() throws Exception {
            signUpWithEmail("buyer@test.local");

            for (int i = 0; i < 4; i++) {
                assertThat(login("buyer@test.local", "Wrong!Password9").getResponse().getStatus())
                        .isEqualTo(401);
            }
            // 5회째에 잠긴다. 실패 카운터가 예외와 함께 롤백됐다면 여기서 401 이 나온다.
            assertThat(login("buyer@test.local", "Wrong!Password9").getResponse().getStatus())
                    .isEqualTo(403);

            // 잠긴 뒤에는 맞는 비밀번호도 통하지 않는다.
            MvcResult afterLock = login("buyer@test.local", PASSWORD);
            assertThat(afterLock.getResponse().getStatus()).isEqualTo(403);
            assertThat(read(afterLock).get("code").asText()).isEqualTo("USER_ACCOUNT_LOCKED");
        }
    }

    // =================================================================
    // 비밀번호 재설정
    // =================================================================

    @Nested
    @DisplayName("비밀번호 재설정")
    class PasswordReset {

        private MvcResult requestReset(String email) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email))))
                    .andReturn();
        }

        private MvcResult confirmReset(String email, String code, String newPassword) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", email, "code", code, "newPassword", newPassword))))
                    .andReturn();
        }

        @Test
        @DisplayName("새 비밀번호로 바꾸고 그것으로 로그인된다")
        void resetsAndSignsIn() throws Exception {
            signUpWithEmail("buyer@test.local");
            String newPassword = "Changed!2026a";

            assertThat(requestReset("buyer@test.local").getResponse().getStatus()).isEqualTo(204);
            MvcResult done = confirmReset("buyer@test.local", mailSender.lastCode(), newPassword);
            assertThat(done.getResponse().getStatus()).isEqualTo(204);

            assertThat(mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local", "password", newPassword))))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);

            // 옛 비밀번호는 더 이상 통하지 않는다.
            assertThat(mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local", "password", PASSWORD))))
                    .andReturn().getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("재설정하면 다른 기기의 세션이 끊긴다 — 빼앗긴 계정을 되찾는 것이 목적이다")
        void revokesOtherSessions() throws Exception {
            signUpWithEmail("buyer@test.local");
            // 가입으로 받은 쿠키가 다른 기기의 세션이라고 본다.
            MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local", "password", PASSWORD))))
                    .andReturn();
            var cookie = login.getResponse().getCookie("hsg_rt");
            assertThat(cookie).isNotNull();

            requestReset("buyer@test.local");
            confirmReset("buyer@test.local", mailSender.lastCode(), "Changed!2026a");

            assertThat(mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("가입하지 않은 주소도 같은 응답이다 — 회원 명단을 훑을 수 없다")
        void unknownEmailLooksTheSame() throws Exception {
            signUpWithEmail("buyer@test.local");

            assertThat(requestReset("buyer@test.local").getResponse().getStatus()).isEqualTo(204);
            assertThat(requestReset("nobody@test.local").getResponse().getStatus()).isEqualTo(204);

            // 회원이 아닌 주소에는 메일이 나가지 않는다.
            assertThat(mailSender.sent.stream().map(StubMailSender.Sent::to))
                    .doesNotContain("nobody@test.local");
        }

        @Test
        @DisplayName("가입 인증번호로 비밀번호를 바꿀 수 없다 — 목적이 다르면 다른 인증이다")
        void signUpCodeCannotResetPassword() throws Exception {
            signUpWithEmail("buyer@test.local");
            // 가입 목적으로 새 번호를 받는다.
            mockMvc.perform(post("/api/v1/auth/email/verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "buyer@test.local"))))
                    .andExpect(status().isNoContent());

            MvcResult result = confirmReset("buyer@test.local", mailSender.lastCode(), "Changed!2026a");
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
        }
    }

    // =================================================================
    // 개인정보가 새어 나가지 않는다
    // =================================================================

    @Nested
    @DisplayName("개인정보 취급")
    class PrivacyGuards {

        @Test
        @DisplayName("가입 토큰을 디코딩해도 이름·생년월일·DI 가 없다 — JWT 는 암호화가 아니다")
        void signUpTokenCarriesNoPersonalData() throws Exception {
            String afterEmail = verifyEmail("buyer@test.local");
            identityProvider.willReturn("DI-SECRET-VALUE", "김철수", "010-1111-2222");
            MvcResult identity = completeIdentity(afterEmail, PASSWORD);

            String token = read(identity).get("signUpToken").asText();
            String payload = new String(java.util.Base64.getUrlDecoder()
                    .decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);

            assertThat(payload).doesNotContain("DI-SECRET-VALUE");
            assertThat(payload).doesNotContain("김철수");
            assertThat(payload).doesNotContain("010-1111-2222");
            assertThat(payload).doesNotContain("1990");
        }

        @Test
        @DisplayName("CI 를 담을 자리가 아예 없다")
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
            signUpWithEmail("buyer@test.local");
            Long id = jdbc.queryForObject(
                    "SELECT id FROM identity_verifications LIMIT 1", Long.class);

            assertThatThrownBySql(() -> jdbc.update(
                    "UPDATE identity_verifications SET name = '남의이름' WHERE id = ?", id));
            assertThatThrownBySql(() -> jdbc.update(
                    "DELETE FROM identity_verifications WHERE id = ?", id));
        }

        private void assertThatThrownBySql(Runnable action) {
            org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("append-only");
        }
    }
}
