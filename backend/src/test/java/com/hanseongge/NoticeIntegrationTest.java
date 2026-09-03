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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공지사항 (STEP 15~16, 설계서 §5 콘텐츠).
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>공개 목록은 <b>로그인 없이</b> 읽히고, 내린 공지는 섞이지 않는다</li>
 *   <li>내린 공지 상세는 403 이 아니라 <b>404</b> — 존재 자체를 알리지 않는다</li>
 *   <li>고정 공지가 <b>목록 맨 위</b>에 온다 (등록 순서와 무관하게)</li>
 *   <li>공개 응답에 <b>작성 관리자 아이디가 실리지 않는다</b></li>
 *   <li>삭제는 행을 지우지 않는다 — {@code active=false} 로 남는다</li>
 *   <li>조회수는 상세를 열 때 오르되 <b>수정 시각을 건드리지 않는다</b></li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-notice-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000"
})
class NoticeIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        AdminUser manager = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "noticemgr", AdminRole.MANAGER);
        managerToken = login(manager.getLoginId());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM notices");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?",
                AdminTestFixtures.PREFIX + "%");
    }

    // =================================================================
    // 공개 조회
    // =================================================================

    @Nested
    @DisplayName("공개 목록")
    class PublicList {

        @Test
        @DisplayName("로그인 없이 읽을 수 있고, 고정 공지가 등록 순서와 무관하게 맨 위에 온다")
        void pinnedComesFirstWithoutLogin() throws Exception {
            create("일반 공지 1", false);
            create("일반 공지 2", false);
            create("고정 공지", true);      // 가장 마지막에 등록했다
            create("일반 공지 3", false);

            mockMvc.perform(get("/api/v1/notices"))   // Authorization 헤더 없음
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.content[0].title").value("고정 공지"))
                    .andExpect(jsonPath("$.content[0].pinned").value(true))
                    // 나머지는 최신순
                    .andExpect(jsonPath("$.content[1].title").value("일반 공지 3"));
        }

        @Test
        @DisplayName("내린 공지는 공개 목록에 섞이지 않는다")
        void hiddenNoticeIsExcluded() throws Exception {
            long visible = create("살아있는 공지", false);
            long hidden = create("내릴 공지", false);

            mockMvc.perform(delete("/api/admin/notices/" + hidden).header("Authorization", bearer()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/notices"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value((int) visible));
        }

        @Test
        @DisplayName("공개 응답에는 작성 관리자 아이디와 본문이 실리지 않는다")
        void publicListHidesAdminIdentityAndBody() throws Exception {
            create("공지", false);

            mockMvc.perform(get("/api/v1/notices"))
                    .andExpect(status().isOk())
                    // 관리자 계정 이름은 외부에 알릴 정보가 아니다
                    .andExpect(jsonPath("$.content[0].createdBy").doesNotExist())
                    // 목록이 쓰지 않는 본문을 20건씩 실어 보내지 않는다
                    .andExpect(jsonPath("$.content[0].content").doesNotExist());
        }
    }

    @Nested
    @DisplayName("공개 상세")
    class PublicDetail {

        @Test
        @DisplayName("내린 공지를 열면 403 이 아니라 404 — 그 자리에 무언가 있다는 사실을 알리지 않는다")
        void hiddenNoticeReturns404() throws Exception {
            long id = create("내릴 공지", false);
            mockMvc.perform(delete("/api/admin/notices/" + id).header("Authorization", bearer()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/notices/" + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
        }

        @Test
        @DisplayName("없는 공지도 같은 404 코드를 준다 — 내린 것과 없는 것이 구분되지 않아야 한다")
        void missingNoticeReturnsSame404() throws Exception {
            mockMvc.perform(get("/api/v1/notices/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
        }

        @Test
        @DisplayName("상세를 열면 조회수가 오르지만 수정 시각은 그대로다")
        void viewCountRisesWithoutTouchingUpdatedAt() throws Exception {
            long id = create("조회수 확인용", false);

            String before = jdbcTemplate.queryForObject(
                    "SELECT updated_at::text FROM notices WHERE id = ?", String.class, id);

            mockMvc.perform(get("/api/v1/notices/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewCount").value(1));

            mockMvc.perform(get("/api/v1/notices/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewCount").value(2));

            String after = jdbcTemplate.queryForObject(
                    "SELECT updated_at::text FROM notices WHERE id = ?", String.class, id);

            // 누가 열어볼 때마다 "수정일"이 바뀌면 목록의 날짜가 의미를 잃는다
            assertThat(after).as("조회는 글의 수정이 아니다").isEqualTo(before);
        }
    }

    // =================================================================
    // 관리자
    // =================================================================

    @Nested
    @DisplayName("관리자 등록·수정·내리기")
    class AdminWrite {

        @Test
        @DisplayName("등록하면 201 이고 바로 공개 목록에 나온다")
        void createdNoticeIsImmediatelyPublic() throws Exception {
            mockMvc.perform(post("/api/admin/notices")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "새 공지", "content", "내용입니다"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.viewCount").value(0))
                    .andExpect(jsonPath("$.createdBy").value(AdminTestFixtures.PREFIX + "noticemgr"));

            mockMvc.perform(get("/api/v1/notices"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            assertThat(countAudit(AuditActions.NOTICE_CREATE)).isEqualTo(1);
        }

        @Test
        @DisplayName("내려도 행이 지워지지 않는다 — 무엇을 안내했는지가 남아야 한다")
        void hideKeepsTheRow() throws Exception {
            long id = create("내릴 공지", false);

            mockMvc.perform(delete("/api/admin/notices/" + id).header("Authorization", bearer()))
                    .andExpect(status().isNoContent());

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notices WHERE id = ?", Integer.class, id);
            assertThat(rows).as("행은 남아 있어야 한다").isEqualTo(1);

            Boolean active = jdbcTemplate.queryForObject(
                    "SELECT active FROM notices WHERE id = ?", Boolean.class, id);
            assertThat(active).isFalse();

            // 관리자 목록에는 그대로 보인다
            mockMvc.perform(get("/api/admin/notices").header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].active").value(false));

            assertThat(countAudit(AuditActions.NOTICE_HIDE)).isEqualTo(1);
        }

        @Test
        @DisplayName("수정하면 감사로그에 이전 값이 남는다")
        void updateRecordsBeforeSnapshot() throws Exception {
            long id = create("원래 제목", false);

            mockMvc.perform(put("/api/admin/notices/" + id)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "바뀐 제목", "content", "바뀐 내용",
                                    "pinned", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("바뀐 제목"))
                    .andExpect(jsonPath("$.pinned").value(true));

            String before = jdbcTemplate.queryForObject(
                    "SELECT before_value::text FROM audit_logs WHERE action = ?",
                    String.class, AuditActions.NOTICE_UPDATE);
            assertThat(before).contains("원래 제목");
        }

        @Test
        @DisplayName("제목이 비면 400")
        void blankTitleRejected() throws Exception {
            mockMvc.perform(post("/api/admin/notices")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "  ", "content", "내용"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("토큰 없이 관리자 API 를 부르면 401")
        void adminApiRequiresToken() throws Exception {
            mockMvc.perform(get("/api/admin/notices"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private long create(String title, boolean pinned) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", title + " 본문");
        body.put("pinned", pinned);

        MvcResult result = mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private int countAudit(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
        return count == null ? 0 : count;
    }

    private String login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId,
                                "password", AdminTestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + managerToken;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
