package com.hanseongge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 환불규정 — 공개 조회, 개정(새 판 발행), append-only 보증.
 *
 * <p>지난 판을 지키는 것이 이 도메인의 핵심이다. 주문이 "그때 동의한 그 내용"을
 * 가리켜야 하므로, 개정은 쌓기만 하고 쌓인 판은 DB 차원에서 고칠 수 없어야 한다.
 *
 * <h2>시드 데이터를 공유한다</h2>
 * V31 이 심은 1판은 지우지 않는다 — 임베디드 PostgreSQL 을 다른 테스트와 공유하고,
 * "운영에서 환불규정은 비어 있을 수 없다"가 전제라서다. 테스트가 발행한 판도 지울 수
 * 없으므로(append-only), 검증은 절대 판수가 아니라 상대 변화로 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-integration-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000"
})
class RefundPolicyIntegrationTest extends EmbeddedPostgresSupport {

    private static final String PUBLIC_URL = "/api/v1/policies/refund";
    private static final String ADMIN_URL = "/api/admin/policies/refund";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;

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

    @Nested
    @DisplayName("공개 조회")
    class PublicRead {

        @Test
        @DisplayName("누구나 현재 판을 본다 — V31 시드가 있어 비어 있을 수 없다")
        void anyoneReadsCurrentVersion() throws Exception {
            mockMvc.perform(get(PUBLIC_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                    // 내부 사정(개정 사유·작성자)은 공개 응답에 담지 않는다
                    .andExpect(jsonPath("$.changeNote").doesNotExist())
                    .andExpect(jsonPath("$.createdBy").doesNotExist());

            String content = readJson(mockMvc.perform(get(PUBLIC_URL)).andReturn(), "$.content");
            assertThat(content).contains("환불");
        }
    }

    @Nested
    @DisplayName("개정 (새 판 발행)")
    class Publish {

        @Test
        @DisplayName("ADMIN 이 발행하면 현재 판이 바뀌고, 지난 판은 이력에 그대로 남는다")
        void publishStacksNewVersion() throws Exception {
            String token = adminToken("rpadmin", AdminRole.ADMIN);
            long before = currentId();

            String newContent = "개정된 환불규정 본문 — " + System.nanoTime();
            mockMvc.perform(post(ADMIN_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", newContent,
                                    "changeNote", "통합테스트 개정"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value(newContent))
                    .andExpect(jsonPath("$.createdBy").value(AdminTestFixtures.PREFIX + "rpadmin"));

            // 쇼핑몰이 보는 현재 판이 즉시 새 판이다
            mockMvc.perform(get(PUBLIC_URL))
                    .andExpect(jsonPath("$.id").value(before + 1))
                    .andExpect(jsonPath("$.content").value(newContent));

            // 지난 판은 이력에 그대로 있다
            mockMvc.perform(get(ADMIN_URL + "/history").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(before + 1))
                    .andExpect(jsonPath("$.content[1].id").value(before));

            // 감사로그 — 어느 판을 누가 왜 발행했는가
            Map<String, Object> audit = jdbcTemplate.queryForMap(
                    "SELECT * FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1",
                    AuditActions.REFUND_POLICY_PUBLISH);
            assertThat(audit.get("target_id")).isEqualTo(String.valueOf(before + 1));
            assertThat(String.valueOf(audit.get("after_value"))).contains("통합테스트 개정");
        }

        @Test
        @DisplayName("직전 판과 같은 내용은 발행되지 않는다 — 저장 두 번 누르는 실수")
        void rejectsUnchangedContent() throws Exception {
            String token = adminToken("rpsame", AdminRole.ADMIN);
            String current = readJson(mockMvc.perform(get(PUBLIC_URL)).andReturn(), "$.content");

            mockMvc.perform(post(ADMIN_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", current, "changeNote", "그대로 저장"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("ADMIN 미만은 발행할 수 없다 — 거래 조건이라 공지보다 문턱이 높다")
        void requiresAdminRole() throws Exception {
            String token = adminToken("rpmgr", AdminRole.MANAGER);
            mockMvc.perform(post(ADMIN_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "권한 없는 개정", "changeNote", "x"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("append-only")
    class AppendOnly {

        @Test
        @DisplayName("쌓인 판은 DB 에서 직접도 고치거나 지울 수 없다")
        void storedVersionsAreImmutable() throws Exception {
            long id = currentId();

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE refund_policies SET content = '몰래 수정' WHERE id = ?", id))
                    .hasMessageContaining("append-only");

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM refund_policies WHERE id = ?", id))
                    .hasMessageContaining("append-only");
        }
    }

    // ---------- 도우미 ----------

    private long currentId() {
        Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM refund_policies", Long.class);
        assertThat(id).as("V31 시드가 1판을 만들어야 한다").isNotNull();
        return id;
    }

    private String adminToken(String shortName, AdminRole role) throws Exception {
        AdminUser admin = AdminTestFixtures.create(adminUserRepository, passwordEncoder, shortName, role);
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", admin.getLoginId(),
                                "password", AdminTestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String readJson(MvcResult result, String path) throws Exception {
        String body = result.getResponse().getContentAsString();
        // $.content 한 단계만 쓰므로 Jackson 으로 충분하다
        return objectMapper.readTree(body).get(path.substring(2)).asText();
    }
}
