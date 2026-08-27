package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.audit.AuditActions;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 5 완료 검증 — 관리자 인증 · 권한 · 감사로그.
 *
 * <p>실제 PostgreSQL 위에서 HTTP 계층부터 검증한다. 서비스 단위 테스트로는 필터체인
 * (JWT 검증, audience 분리, 권한 계층)이 실제로 걸리는지 확인할 수 없다 — STEP 5 에서
 * 틀리면 위험한 부분이 대부분 그 필터체인에 있다.
 *
 * <h2>테스트 격리</h2>
 * 임베디드 PostgreSQL 은 JVM 당 하나이고 테스트 클래스들이 공유한다. 그래서
 * <ul>
 *   <li>계정은 {@code test_} 접두사로만 만들고, 정리도 그 접두사만 지운다 —
 *       부트스트랩 테스트가 만든 최초 관리자를 건드리지 않는다.</li>
 *   <li>Rate Limit 버킷은 IP 단위이므로 테스트마다 다른 {@code X-Forwarded-For} 를 쓴다.
 *       한 테스트의 시도 횟수가 다른 테스트의 한도를 갉아먹지 않는다.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 재기동 없는 단일 컨텍스트지만, 랜덤 키 경로 대신 실제 설정 경로를 타게 한다.
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        // 이 클래스의 관심사는 Rate Limit 이 아니다. 별도 클래스에서 따로 검증한다.
        "app.admin-security.login-rate-per-minute=1000"
})
class AdminAuthIntegrationTest extends EmbeddedPostgresSupport {

    private static final String LOGIN_URL = "/api/admin/auth/login";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetFixtures() {
        cleanUp(jdbcTemplate);
    }

    /**
     * 테스트가 끝날 때도 지운다. 마지막 테스트가 남긴 데이터가 다른 테스트 클래스
     * (같은 PostgreSQL 인스턴스를 공유한다)의 전제를 깨뜨리지 않게 한다.
     */
    @AfterEach
    void cleanUpAfterTest() {
        cleanUp(jdbcTemplate);
    }

    private static void cleanUp(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?", AdminTestFixtures.PREFIX + "%");
    }

    // =================================================================
    // 1. 로그인
    // =================================================================

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공하면 audience=admin 토큰을 발급하고 ADMIN_LOGIN 을 감사로그에 남긴다")
        void success() throws Exception {
            AdminUser admin = fixture("super", AdminRole.SUPER_ADMIN);

            MvcResult result = mockMvc.perform(loginRequest(admin.getLoginId(),
                    AdminTestFixtures.PASSWORD, "203.0.113.10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(1800))
                    .andExpect(jsonPath("$.admin.loginId").value(admin.getLoginId()))
                    .andExpect(jsonPath("$.admin.role").value("SUPER_ADMIN"))
                    // 비밀번호 해시가 응답에 새어 나오지 않아야 한다
                    .andExpect(jsonPath("$.admin.passwordHash").doesNotExist())
                    .andReturn();

            // 발급된 토큰이 실제로 관리자 API 를 통과한다
            String token = readJson(result).get("accessToken").asText();
            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value(admin.getLoginId()));

            Map<String, Object> log = latestAudit(AuditActions.ADMIN_LOGIN);
            assertThat(log.get("actor")).isEqualTo(admin.getLoginId());
            assertThat(log.get("actor_type")).isEqualTo("ADMIN");
            assertThat(log.get("target_type")).isEqualTo(AuditActions.TARGET_ADMIN_USER);
            assertThat(log.get("ip")).isEqualTo("203.0.113.10");
            assertThat(String.valueOf(log.get("after_value"))).contains("SUPER_ADMIN");
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401 과 함께 실패 횟수를 누적하고 감사로그를 남긴다")
        void wrongPassword() throws Exception {
            AdminUser admin = fixture("wrongpw", AdminRole.ADMIN);

            mockMvc.perform(loginRequest(admin.getLoginId(), "Wrong!Passw0rd", "203.0.113.11"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

            // 실패 카운터가 롤백되지 않고 남아야 한다 (별도 트랜잭션 분리의 핵심)
            AdminUser reloaded = adminUserRepository.findById(admin.getId()).orElseThrow();
            assertThat(reloaded.getLoginFailCount()).isEqualTo(1);
            assertThat(reloaded.getStatus()).isEqualTo(AdminStatus.ACTIVE);

            assertThat(latestAudit(AuditActions.ADMIN_LOGIN_FAILED)).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 아이디도 같은 401 을 돌려주고, 시도한 아이디를 감사로그에 남긴다")
        void unknownLoginId() throws Exception {
            mockMvc.perform(loginRequest(AdminTestFixtures.PREFIX + "ghost", "Any!Passw0rd", "203.0.113.12"))
                    .andExpect(status().isUnauthorized())
                    // 계정 존재 여부를 응답으로 구분해 주지 않는다
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

            Map<String, Object> log = latestAudit(AuditActions.ADMIN_LOGIN_FAILED);
            assertThat(log.get("actor")).isEqualTo(AdminTestFixtures.PREFIX + "ghost");
            assertThat(log.get("target_type")).isEqualTo(AuditActions.TARGET_LOGIN_ID);
            assertThat(String.valueOf(log.get("after_value"))).contains("UNKNOWN_LOGIN_ID");
        }
    }

    // =================================================================
    // 2. 계정 잠금 (5회 실패)
    // =================================================================

    @Nested
    @DisplayName("계정 잠금")
    class Lockout {

        @Test
        @DisplayName("연속 5회 실패하면 LOCKED 가 되고, 이후에는 올바른 비밀번호도 거부한다")
        void locksAfterFiveFailures() throws Exception {
            AdminUser admin = fixture("lock", AdminRole.MANAGER);

            for (int i = 1; i <= 5; i++) {
                mockMvc.perform(loginRequest(admin.getLoginId(), "Wrong!Passw0rd", "203.0.113.20"))
                        .andExpect(status().isUnauthorized());
            }

            AdminUser locked = adminUserRepository.findById(admin.getId()).orElseThrow();
            assertThat(locked.getStatus()).isEqualTo(AdminStatus.LOCKED);
            assertThat(locked.getLoginFailCount()).isEqualTo(5);
            assertThat(locked.getLockedAt()).isNotNull();

            // 잠긴 뒤에는 비밀번호가 맞아도 통과하지 못한다 — 잠금이 실질적이어야 한다
            mockMvc.perform(loginRequest(admin.getLoginId(), AdminTestFixtures.PASSWORD, "203.0.113.20"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_LOCKED"));

            assertThat(countAudit(AuditActions.ADMIN_LOGIN_FAILED)).isEqualTo(5);
            assertThat(countAudit(AuditActions.ADMIN_ACCOUNT_AUTO_LOCKED)).isEqualTo(1);
            assertThat(countAudit(AuditActions.ADMIN_LOGIN_BLOCKED)).isEqualTo(1);
        }

        @Test
        @DisplayName("중간에 성공하면 실패 카운터가 0 으로 돌아간다")
        void successResetsCounter() throws Exception {
            AdminUser admin = fixture("reset", AdminRole.ADMIN);

            mockMvc.perform(loginRequest(admin.getLoginId(), "Wrong!Passw0rd", "203.0.113.21"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(loginRequest(admin.getLoginId(), "Wrong!Passw0rd", "203.0.113.21"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(loginRequest(admin.getLoginId(), AdminTestFixtures.PASSWORD, "203.0.113.21"))
                    .andExpect(status().isOk());

            assertThat(adminUserRepository.findById(admin.getId()).orElseThrow().getLoginFailCount())
                    .isZero();
        }
    }

    // =================================================================
    // 3. 권한 (계층 + @PreAuthorize)
    // =================================================================

    @Nested
    @DisplayName("권한")
    class Authorization {

        @Test
        @DisplayName("OPERATOR 는 관리자 계정 API(SUPER_ADMIN 전용)에서 403 을 받는다")
        void operatorDeniedOnAdminCrud() throws Exception {
            String token = tokenOf("operator", AdminRole.OPERATOR, "203.0.113.30");

            mockMvc.perform(get("/api/admin/admins").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("MANAGER 는 감사로그(ADMIN 이상)에서 403 을 받는다")
        void managerDeniedOnAuditLogs() throws Exception {
            String token = tokenOf("manager", AdminRole.MANAGER, "203.0.113.31");

            mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("권한 계층 — SUPER_ADMIN 은 ADMIN 전용 API 를 통과한다")
        void roleHierarchyApplies() throws Exception {
            String token = tokenOf("hier", AdminRole.SUPER_ADMIN, "203.0.113.32");

            // hasRole('ADMIN') 인 엔드포인트를 SUPER_ADMIN 이 통과 = 계층 설정이 살아 있다
            mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("토큰이 없거나 위조되면 401 이고, 경로 존재 여부를 노출하지 않는다")
        void rejectsMissingOrForgedToken() throws Exception {
            mockMvc.perform(get("/api/admin/admins"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            mockMvc.perform(get("/api/admin/admins")
                            .header("Authorization", "Bearer not.a.real.token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }

        @Test
        @DisplayName("Refresh 토큰으로는 일반 관리자 API 를 호출할 수 없다")
        void refreshTokenIsNotAnAccessToken() throws Exception {
            AdminUser admin = fixture("refresh", AdminRole.SUPER_ADMIN);
            MvcResult result = mockMvc.perform(loginRequest(admin.getLoginId(),
                            AdminTestFixtures.PASSWORD, "203.0.113.33"))
                    .andExpect(status().isOk())
                    .andReturn();
            String refreshToken = readJson(result).get("refreshToken").asText();

            mockMvc.perform(get("/api/admin/admins").header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

            // 재발급 엔드포인트에서는 정상 동작해야 한다
            mockMvc.perform(post("/api/admin/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
    }

    // =================================================================
    // 4. 로그아웃 (토큰 무효화)
    // =================================================================

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃하면 기존 토큰이 즉시 무효가 되고 감사로그가 남는다")
        void invalidatesIssuedToken() throws Exception {
            AdminUser admin = fixture("logout", AdminRole.ADMIN);
            String token = login(admin.getLoginId(), "203.0.113.40");

            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/admin/auth/logout").header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            // 무상태 JWT 라도 토큰 세대(tv) 대조로 실제 회수가 이뤄진다
            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

            assertThat(countAudit(AuditActions.ADMIN_LOGOUT)).isEqualTo(1);
        }
    }

    // =================================================================
    // 5. 관리자 계정 CRUD + @AuditLog AOP
    // =================================================================

    @Nested
    @DisplayName("관리자 계정 관리")
    class AdminCrud {

        @Test
        @DisplayName("계정을 생성하면 @AuditLog 가 actor·target·after 를 자동으로 남긴다")
        void createRecordsAudit() throws Exception {
            String token = tokenOf("creator", AdminRole.SUPER_ADMIN, "203.0.113.50");

            String newLoginId = AdminTestFixtures.PREFIX + "created";
            mockMvc.perform(post("/api/admin/admins")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Forwarded-For", "203.0.113.50")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "loginId", newLoginId,
                                    "password", "New!Passw0rd1",
                                    "name", "신규 담당자",
                                    "role", "MANAGER"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.loginId").value(newLoginId))
                    .andExpect(jsonPath("$.role").value("MANAGER"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            Map<String, Object> log = latestAudit(AuditActions.ADMIN_CREATE);
            assertThat(log.get("actor")).isEqualTo(AdminTestFixtures.PREFIX + "creator");
            assertThat(log.get("target_type")).isEqualTo(AuditActions.TARGET_ADMIN_USER);
            assertThat(log.get("target_id")).isNotNull();
            assertThat(log.get("ip")).isEqualTo("203.0.113.50");

            String after = String.valueOf(log.get("after_value"));
            assertThat(after).contains(newLoginId);
            // 평문 비밀번호가 감사로그에 남으면 안 된다 (감사로그는 지우지 않는 테이블이다)
            assertThat(after).doesNotContain("New!Passw0rd1");
        }

        @Test
        @DisplayName("중복 아이디는 409 이고, 실패도 _FAILED 로 감사로그에 남는다")
        void duplicateLoginIdIsRejected() throws Exception {
            String token = tokenOf("dup", AdminRole.SUPER_ADMIN, "203.0.113.51");
            AdminUser existing = fixture("existing", AdminRole.MANAGER);

            mockMvc.perform(post("/api/admin/admins")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "loginId", existing.getLoginId(),
                                    "password", "New!Passw0rd1",
                                    "name", "중복",
                                    "role", "MANAGER"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_ID_DUPLICATED"));

            assertThat(countAudit(AuditActions.ADMIN_CREATE + "_FAILED")).isEqualTo(1);
        }

        @Test
        @DisplayName("상태 변경은 before/after 를 모두 감사로그에 남긴다")
        void statusChangeRecordsBeforeAndAfter() throws Exception {
            String token = tokenOf("changer", AdminRole.SUPER_ADMIN, "203.0.113.52");
            AdminUser target = fixture("target", AdminRole.OPERATOR);

            mockMvc.perform(patch("/api/admin/admins/" + target.getId() + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "status", "DISABLED",
                                    "reason", "퇴사 처리"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISABLED"));

            Map<String, Object> log = latestAudit(AuditActions.ADMIN_STATUS_CHANGE);
            assertThat(String.valueOf(log.get("before_value"))).contains("ACTIVE");
            assertThat(String.valueOf(log.get("after_value")))
                    .contains("DISABLED")
                    .contains("퇴사 처리");
        }

        @Test
        @DisplayName("본인 계정의 상태는 바꿀 수 없다 — 스스로를 잠가 복구 불능이 되는 것을 막는다")
        void cannotChangeOwnStatus() throws Exception {
            AdminUser me = fixture("self", AdminRole.SUPER_ADMIN);
            AdminUser other = fixture("other_super", AdminRole.SUPER_ADMIN);
            assertThat(other.getId()).isNotNull();
            String token = login(me.getLoginId(), "203.0.113.53");

            mockMvc.perform(patch("/api/admin/admins/" + me.getId() + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "status", "DISABLED",
                                    "reason", "실수"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN_SELF_MODIFICATION_DENIED"));
        }

        @Test
        @DisplayName("사유가 없으면 상태 변경이 400 으로 거부된다")
        void statusChangeRequiresReason() throws Exception {
            String token = tokenOf("noreason", AdminRole.SUPER_ADMIN, "203.0.113.54");
            AdminUser target = fixture("noreason_target", AdminRole.OPERATOR);

            mockMvc.perform(patch("/api/admin/admins/" + target.getId() + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors.reason").exists());
        }

        @Test
        @DisplayName("비밀번호를 바꾸면 본인의 기존 토큰이 전부 무효가 된다")
        void passwordChangeInvalidatesTokens() throws Exception {
            AdminUser admin = fixture("pwchange", AdminRole.ADMIN);
            String token = login(admin.getLoginId(), "203.0.113.55");

            mockMvc.perform(patch("/api/admin/auth/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "currentPassword", AdminTestFixtures.PASSWORD,
                                    "newPassword", "Changed!Passw0rd"))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());

            // 새 비밀번호로는 로그인되고, 옛 비밀번호로는 안 된다
            mockMvc.perform(loginRequest(admin.getLoginId(), "Changed!Passw0rd", "203.0.113.55"))
                    .andExpect(status().isOk());
            mockMvc.perform(loginRequest(admin.getLoginId(), AdminTestFixtures.PASSWORD, "203.0.113.55"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("본문이 깨진 JSON 이면 500 이 아니라 400 을 돌려준다")
        void malformedBodyIsBadRequest() throws Exception {
            String token = tokenOf("malformed", AdminRole.SUPER_ADMIN, "203.0.113.57");

            mockMvc.perform(post("/api/admin/admins")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("비밀번호 정책(영문+숫자+특수문자 10자 이상)에 어긋나면 400")
        void weakPasswordIsRejected() throws Exception {
            String token = tokenOf("weakpw", AdminRole.SUPER_ADMIN, "203.0.113.56");

            mockMvc.perform(post("/api/admin/admins")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "loginId", AdminTestFixtures.PREFIX + "weak",
                                    "password", "12345678",
                                    "name", "약한 비번",
                                    "role", "OPERATOR"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    // =================================================================
    // 6. 감사로그 조회
    // =================================================================

    @Nested
    @DisplayName("감사로그 조회")
    class AuditSearch {

        @Test
        @DisplayName("actor / action / 기간으로 검색되고 페이징된다")
        void searchAndPaging() throws Exception {
            AdminUser viewer = fixture("viewer", AdminRole.SUPER_ADMIN);
            AdminUser noisy = fixture("noisy", AdminRole.OPERATOR);

            // noisy 계정으로 실패 3건을 만든다
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(loginRequest(noisy.getLoginId(), "Wrong!Passw0rd", "203.0.113.60"))
                        .andExpect(status().isUnauthorized());
            }
            String token = login(viewer.getLoginId(), "203.0.113.61");

            // action 정확 일치
            mockMvc.perform(get("/api/admin/audit-logs")
                            .header("Authorization", "Bearer " + token)
                            .param("action", AuditActions.ADMIN_LOGIN_FAILED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content[0].action").value(AuditActions.ADMIN_LOGIN_FAILED));

            // actor 부분 일치
            mockMvc.perform(get("/api/admin/audit-logs")
                            .header("Authorization", "Bearer " + token)
                            .param("actor", "noisy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3));

            // 페이징
            mockMvc.perform(get("/api/admin/audit-logs")
                            .header("Authorization", "Bearer " + token)
                            .param("action", AuditActions.ADMIN_LOGIN_FAILED)
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.last").value(false));

            // 기간 — 먼 과거 구간에는 아무것도 없어야 한다
            mockMvc.perform(get("/api/admin/audit-logs")
                            .header("Authorization", "Bearer " + token)
                            .param("from", "2020-01-01T00:00:00+09:00")
                            .param("to", "2020-02-01T00:00:00+09:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private AdminUser fixture(String shortName, AdminRole role) {
        return AdminTestFixtures.create(adminUserRepository, passwordEncoder, shortName, role);
    }

    /** 계정을 만들고 로그인해서 Access 토큰까지 받아온다. */
    private String tokenOf(String shortName, AdminRole role, String ip) throws Exception {
        AdminUser admin = fixture(shortName, role);
        return login(admin.getLoginId(), ip);
    }

    private String login(String loginId, String ip) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(loginId, AdminTestFixtures.PASSWORD, ip))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder loginRequest(String loginId, String password, String ip)
            throws Exception {
        return post(LOGIN_URL)
                .header("X-Forwarded-For", ip)
                .header("User-Agent", "integration-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("loginId", loginId, "password", password)));
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
