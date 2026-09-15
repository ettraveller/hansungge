package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.auth.AdminJwtProvider;
import com.hanseongge.global.auth.TotpGenerator;
import com.hanseongge.support.AdminTestFixtures;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 2단계 인증(OTP) — 등록, 로그인, 재사용 차단, 잠금, 복구 코드, 최고관리자 초기화.
 *
 * <p>인증 앱 대신 {@link TotpGenerator} 로 6자리를 계산한다. 서버와 같은 계산이지만 RFC 시험값은
 * {@code TotpGeneratorTest} 가 따로 고정하므로 "둘 다 틀려서 맞는" 경우는 없다.
 *
 * <p>같은 30초 구간의 6자리는 한 번만 통과한다. 한 테스트 안에서 등록 뒤 로그인까지 이어 가려면
 * {@link #forgetUsedStep} 으로 그 기록을 지운다 — 실제로는 30초를 기다리는 것과 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000",
        "app.admin-security.otp-required=true"
})
class AdminOtpIntegrationTest extends EmbeddedPostgresSupport {

    private static final String LOGIN_URL = "/api/admin/auth/login";
    private static final String VERIFY_URL = "/api/admin/auth/otp/verify";
    private static final String SETUP_URL = "/api/admin/auth/otp/setup";
    private static final String ACTIVATE_URL = "/api/admin/auth/otp/activate";
    private static final String IP = "198.51.100.20";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminJwtProvider jwtProvider;

    @BeforeEach
    void resetFixtures() {
        cleanUp();
    }

    @AfterEach
    void cleanUpAfterTest() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?", AdminTestFixtures.PREFIX + "%");
    }

    // =================================================================
    // 첫 등록
    // =================================================================

    @Nested
    @DisplayName("첫 로그인 — 인증 앱 등록")
    class FirstEnrollment {

        @Test
        @DisplayName("비밀번호만으로는 토큰이 나오지 않고, 등록을 마쳐야 로그인된다")
        void passwordAloneGivesNoToken() throws Exception {
            AdminUser admin = fixture("enroll", AdminRole.ADMIN);

            JsonNode login = readJson(mockMvc.perform(loginRequest(admin.getLoginId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.step").value("OTP_SETUP_REQUIRED"))
                    .andExpect(jsonPath("$.stepToken").isNotEmpty())
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andReturn());
            String stepToken = login.get("stepToken").asText();

            assertThat(countAudit(AuditActions.ADMIN_LOGIN_PASSWORD_VERIFIED)).isEqualTo(1);
            assertThat(countAudit(AuditActions.ADMIN_LOGIN)).as("OTP 전에는 로그인 성공이 아니다").isZero();
            assertThat(adminUserRepository.findById(admin.getId()).orElseThrow().getLastLoginAt()).isNull();

            // 2단계 표로는 관리자 API 를 부를 수 없다
            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + stepToken))
                    .andExpect(status().isUnauthorized());

            JsonNode setup = readJson(mockMvc.perform(post(SETUP_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("stepToken", stepToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountName").value(admin.getLoginId()))
                    .andReturn());
            String secretText = setup.get("secret").asText();
            assertThat(setup.get("otpauthUri").asText())
                    .startsWith("otpauth://totp/")
                    .contains("secret=" + secretText.replace(" ", ""))
                    .contains("digits=6")
                    .contains("period=30");

            // 키는 평문으로 DB 에 남지 않는다
            String stored = jdbcTemplate.queryForObject(
                    "SELECT otp_secret FROM admin_users WHERE id = ?", String.class, admin.getId());
            assertThat(stored).startsWith("v1:").doesNotContain(secretText.replace(" ", ""));

            byte[] secret = TotpGenerator.fromBase32(secretText);

            // 틀린 숫자로는 켜지지 않는다
            mockMvc.perform(activateRequest(stepToken, wrongCode(secret)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_INVALID"));
            assertThat(adminUserRepository.findById(admin.getId()).orElseThrow().isOtpEnabled()).isFalse();

            JsonNode activated = readJson(mockMvc.perform(activateRequest(stepToken, currentCode(secret)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                    .andExpect(jsonPath("$.login.step").value("AUTHENTICATED"))
                    .andExpect(jsonPath("$.login.accessToken").isNotEmpty())
                    .andReturn());

            String accessToken = activated.get("login").get("accessToken").asText();
            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.otpEnabled").value(true));

            assertThat(activated.get("recoveryCodes").get(0).asText()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
            assertThat(countAudit(AuditActions.ADMIN_OTP_ENABLED)).isEqualTo(1);
            assertThat(String.valueOf(latestAudit(AuditActions.ADMIN_LOGIN).get("after_value")))
                    .contains("TOTP");
        }

        @Test
        @DisplayName("등록 표로 6자리 확인을 부르거나 위조 표를 쓰면 처음부터 다시 로그인하게 한다")
        void wrongOrForgedStepToken() throws Exception {
            AdminUser admin = fixture("steptoken", AdminRole.ADMIN);
            String setupToken = readJson(mockMvc.perform(loginRequest(admin.getLoginId())).andReturn())
                    .get("stepToken").asText();

            mockMvc.perform(verifyRequest(setupToken, "123456"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_STEP_EXPIRED"));

            mockMvc.perform(verifyRequest("not-a-token", "123456"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_STEP_EXPIRED"));

            mockMvc.perform(post(SETUP_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("OTP 가 필수인데 등록하지 않은 계정은 토큰을 재발급받지 못한다")
        void refreshRequiresEnrollment() throws Exception {
            AdminUser admin = fixture("norefresh", AdminRole.ADMIN);

            mockMvc.perform(post("/api/admin/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", jwtProvider.issueRefreshToken(admin)))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }
    }

    // =================================================================
    // 등록 이후 로그인
    // =================================================================

    @Nested
    @DisplayName("등록한 관리자의 로그인")
    class EnrolledLogin {

        @Test
        @DisplayName("6자리로 들어오고, 같은 6자리는 다시 쓸 수 없다")
        void codeCannotBeReplayed() throws Exception {
            Enrolled enrolled = enroll("replay", AdminRole.ADMIN);
            String code = currentCode(enrolled.secret());

            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.step").value("AUTHENTICATED"))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.recoveryCodesRemaining").doesNotExist());

            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), code))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_INVALID"));
        }

        @Test
        @DisplayName("연속 5회 틀리면 잠기고, 잠긴 뒤에는 그 표도 쓸 수 없다")
        void locksAfterFiveFailures() throws Exception {
            Enrolled enrolled = enroll("otplock", AdminRole.ADMIN);
            String stepToken = challenge(enrolled.admin());
            String wrong = wrongCode(enrolled.secret());

            for (int i = 1; i <= 4; i += 1) {
                mockMvc.perform(verifyRequest(stepToken, wrong))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("ADMIN_OTP_INVALID"));
            }
            mockMvc.perform(verifyRequest(stepToken, wrong))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_LOCKED"));

            AdminUser locked = adminUserRepository.findById(enrolled.admin().getId()).orElseThrow();
            assertThat(locked.getStatus()).isEqualTo(AdminStatus.LOCKED);
            assertThat(locked.getOtpFailCount()).isEqualTo(5);
            assertThat(countAudit(AuditActions.ADMIN_OTP_FAILED)).isEqualTo(5);
            assertThat(String.valueOf(latestAudit(AuditActions.ADMIN_ACCOUNT_AUTO_LOCKED).get("after_value")))
                    .contains("OTP_FAILURES");

            // 맞는 숫자를 넣어도 이미 늦었다
            mockMvc.perform(verifyRequest(stepToken, currentCode(enrolled.secret())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_STEP_EXPIRED"));
        }

        @Test
        @DisplayName("비밀번호로 다시 로그인해도 OTP 실패 횟수는 되돌아가지 않는다")
        void passwordLoginDoesNotResetOtpFailures() throws Exception {
            Enrolled enrolled = enroll("separate", AdminRole.ADMIN);
            String wrong = wrongCode(enrolled.secret());

            for (int i = 0; i < 4; i += 1) {
                mockMvc.perform(verifyRequest(challenge(enrolled.admin()), wrong))
                        .andExpect(status().isBadRequest());
            }
            assertThat(adminUserRepository.findById(enrolled.admin().getId()).orElseThrow().getOtpFailCount())
                    .isEqualTo(4);

            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), wrong))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_LOCKED"));
        }

        @Test
        @DisplayName("복구 코드는 대소문자·하이픈을 가리지 않고 한 번만 통과한다")
        void recoveryCodeIsSingleUse() throws Exception {
            Enrolled enrolled = enroll("recovery", AdminRole.ADMIN);
            String typed = enrolled.recoveryCodes().get(3).toLowerCase().replace("-", "");

            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), typed))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.recoveryCodesRemaining").value(9));

            assertThat(countAudit(AuditActions.ADMIN_OTP_RECOVERY_USED)).isEqualTo(1);
            assertThat(String.valueOf(latestAudit(AuditActions.ADMIN_LOGIN).get("after_value")))
                    .contains("RECOVERY_CODE");

            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), typed))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_INVALID"));
        }
    }

    // =================================================================
    // 내 계정
    // =================================================================

    @Nested
    @DisplayName("내 계정의 OTP")
    class MyAccount {

        @Test
        @DisplayName("상태를 보여주고, 이미 등록했으면 다시 등록할 수 없다")
        void statusAndNoReEnrollment() throws Exception {
            Enrolled enrolled = enroll("mystatus", AdminRole.ADMIN);

            mockMvc.perform(get("/api/admin/auth/otp").header("Authorization", bearer(enrolled)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.required").value(true))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.recoveryCodesRemaining").value(10));

            mockMvc.perform(post(SETUP_URL).header("Authorization", bearer(enrolled)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_ALREADY_ENABLED"));
        }

        @Test
        @DisplayName("복구 코드를 새로 받으려면 지금 6자리가 필요하고, 옛 코드는 모두 무효가 된다")
        void regenerateRecoveryCodes() throws Exception {
            Enrolled enrolled = enroll("regen", AdminRole.ADMIN);

            mockMvc.perform(post("/api/admin/auth/otp/recovery-codes")
                            .header("Authorization", bearer(enrolled))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("code", wrongCode(enrolled.secret())))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ADMIN_OTP_INVALID"));

            JsonNode fresh = readJson(mockMvc.perform(post("/api/admin/auth/otp/recovery-codes")
                            .header("Authorization", bearer(enrolled))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("code", currentCode(enrolled.secret())))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                    .andReturn());

            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), enrolled.recoveryCodes().get(0)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(verifyRequest(challenge(enrolled.admin()), fresh.get("recoveryCodes").get(0).asText()))
                    .andExpect(status().isOk());
        }
    }

    // =================================================================
    // 최고관리자 초기화
    // =================================================================

    @Nested
    @DisplayName("최고관리자의 OTP 초기화")
    class SuperAdminReset {

        @Test
        @DisplayName("휴대폰을 잃은 관리자의 OTP 를 초기화하면 잠금이 풀리고 다음 로그인에서 다시 등록한다")
        void resetOtherAdmin() throws Exception {
            Enrolled boss = enroll("boss", AdminRole.SUPER_ADMIN);
            Enrolled lost = enroll("lostphone", AdminRole.ADMIN);
            jdbcTemplate.update("UPDATE admin_users SET status = 'LOCKED', otp_fail_count = 5 WHERE id = ?",
                    lost.admin().getId());

            mockMvc.perform(patch("/api/admin/admins/{id}/otp-reset", lost.admin().getId())
                            .header("Authorization", bearer(boss))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("reason", "휴대폰 분실 — 본인 확인"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.otpEnabled").value(false))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            Integer codes = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM admin_otp_recovery_codes WHERE admin_id = ?", Integer.class,
                    lost.admin().getId());
            assertThat(codes).isZero();
            assertThat(String.valueOf(latestAudit(AuditActions.ADMIN_OTP_RESET).get("after_value")))
                    .contains("휴대폰 분실");

            // 잃어버린 휴대폰에 열려 있던 세션도 끊긴다
            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", bearer(lost)))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(loginRequest(lost.admin().getLoginId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.step").value("OTP_SETUP_REQUIRED"));
        }

        @Test
        @DisplayName("본인 OTP 는 초기화할 수 없고, 최고관리자가 아니면 부를 수 없다")
        void selfAndNonSuperDenied() throws Exception {
            Enrolled boss = enroll("selfreset", AdminRole.SUPER_ADMIN);
            Enrolled plain = enroll("plainadmin", AdminRole.ADMIN);

            mockMvc.perform(patch("/api/admin/admins/{id}/otp-reset", boss.admin().getId())
                            .header("Authorization", bearer(boss))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("reason", "테스트"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN_SELF_MODIFICATION_DENIED"));

            mockMvc.perform(patch("/api/admin/admins/{id}/otp-reset", boss.admin().getId())
                            .header("Authorization", bearer(plain))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("reason", "테스트"))))
                    .andExpect(status().isForbidden());
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private record Enrolled(AdminUser admin, byte[] secret, List<String> recoveryCodes, String accessToken) {
    }

    /** 첫 로그인 → 등록 → 완료까지 HTTP 로 거친다. 쓴 30초 구간은 지워 둔다. */
    private Enrolled enroll(String shortName, AdminRole role) throws Exception {
        AdminUser admin = fixture(shortName, role);
        String stepToken = readJson(mockMvc.perform(loginRequest(admin.getLoginId()))
                .andExpect(jsonPath("$.step").value("OTP_SETUP_REQUIRED"))
                .andReturn()).get("stepToken").asText();

        String secretText = readJson(mockMvc.perform(post(SETUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stepToken", stepToken))))
                .andExpect(status().isOk())
                .andReturn()).get("secret").asText();
        byte[] secret = TotpGenerator.fromBase32(secretText);

        JsonNode activated = readJson(mockMvc.perform(activateRequest(stepToken, currentCode(secret)))
                .andExpect(status().isOk())
                .andReturn());

        List<String> codes = new ArrayList<>();
        activated.get("recoveryCodes").forEach(node -> codes.add(node.asText()));
        forgetUsedStep(admin);
        return new Enrolled(admin, secret, codes, activated.get("login").get("accessToken").asText());
    }

    private void forgetUsedStep(AdminUser admin) {
        jdbcTemplate.update("UPDATE admin_users SET otp_last_step = NULL WHERE id = ?", admin.getId());
    }

    private String challenge(AdminUser admin) throws Exception {
        return readJson(mockMvc.perform(loginRequest(admin.getLoginId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("OTP_REQUIRED"))
                .andReturn()).get("stepToken").asText();
    }

    private static String currentCode(byte[] secret) {
        return TotpGenerator.code(secret, TotpGenerator.currentStep());
    }

    /** 허용 구간(앞뒤 1) 어디와도 맞지 않는 6자리. */
    private static String wrongCode(byte[] secret) {
        long step = TotpGenerator.currentStep();
        Set<String> valid = Set.of(
                TotpGenerator.code(secret, step - 2), TotpGenerator.code(secret, step - 1),
                TotpGenerator.code(secret, step), TotpGenerator.code(secret, step + 1),
                TotpGenerator.code(secret, step + 2));
        for (int candidate = 0; ; candidate += 1) {
            String code = String.format("%06d", candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private static String bearer(Enrolled enrolled) {
        return "Bearer " + enrolled.accessToken();
    }

    private AdminUser fixture(String shortName, AdminRole role) {
        return AdminTestFixtures.create(adminUserRepository, passwordEncoder, shortName, role);
    }

    private MockHttpServletRequestBuilder loginRequest(String loginId) throws Exception {
        return post(LOGIN_URL)
                .header("X-Forwarded-For", IP)
                .header("User-Agent", "integration-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("loginId", loginId, "password", AdminTestFixtures.PASSWORD)));
    }

    private MockHttpServletRequestBuilder verifyRequest(String stepToken, String code) throws Exception {
        return post(VERIFY_URL)
                .header("X-Forwarded-For", IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("stepToken", stepToken, "code", code)));
    }

    private MockHttpServletRequestBuilder activateRequest(String stepToken, String code) throws Exception {
        return post(ACTIVATE_URL)
                .header("X-Forwarded-For", IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("stepToken", stepToken, "code", code)));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> latestAudit(String action) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1", action);
        assertThat(rows).as("감사로그 action=%s 가 남아야 한다", action).isNotEmpty();
        return rows.get(0);
    }

    private int countAudit(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
        return count == null ? 0 : count;
    }
}
