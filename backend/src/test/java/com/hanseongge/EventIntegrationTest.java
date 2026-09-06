package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.support.AdminTestFixtures;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이벤트 · 응모 (설계서 §5 콘텐츠).
 *
 * <p>이벤트의 규칙은 <b>기간</b>과 <b>1인 1회</b> 두 가지다. 둘 다 화면에서 버튼을 감추는
 * 것으로는 지킬 수 없다 — 종료 직전에 열어 둔 화면에서 누르거나 API 를 직접 부르면
 * 그대로 들어온다. 서버가 막는지를 고정한다.
 *
 * <ul>
 *   <li><b>기간 밖에는 응모할 수 없다</b> — 시작 전(UPCOMING)도 종료 후(CLOSED)도</li>
 *   <li><b>같은 사람이 두 번 응모할 수 없다</b> — 막지 않으면 이벤트가 성립하지 않는다</li>
 *   <li><b>남의 응모는 취소할 수 없다</b> — 여기서도 존재를 알리지 않는 404 다</li>
 *   <li><b>응모자명을 서버가 가린다</b> — 문의게시판과 같은 규칙이다</li>
 *   <li><b>내린 이벤트는 404</b> — 공개 목록에도 섞이지 않는다</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-event-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000",
        "spring.main.allow-bean-definition-overriding=true"
})
class EventIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StubOAuthProvider google;
    @Autowired StubIdentityVerificationProvider identityProvider;

    /** 가입 4단계를 한 줄로 태운다. 단계별 검증이 목적인 테스트는 쓰지 않는다. */
    private SignUpFlow flow;

    private String entrantToken;
    private String strangerToken;
    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        google.reset();
        identityProvider.reset();
        flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);

        entrantToken = signUp("entrant-1", "entrant@test.local", "김철수");
        strangerToken = signUp("stranger-2", "stranger2@test.local", "박영희");

        AdminUser manager = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "eventmgr", AdminRole.MANAGER);
        managerToken = adminLogin(manager.getLoginId());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM event_entries");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM audit_logs");
        UserTestFixtures.deleteAll(jdbc);
        jdbc.update("DELETE FROM admin_users WHERE login_id LIKE ?", AdminTestFixtures.PREFIX + "%");
    }

    // =================================================================
    // 응모 기간 — 화면이 아니라 서버가 막는다
    // =================================================================

    @Nested
    @DisplayName("응모 기간")
    class EntryPeriod {

        @Test
        @DisplayName("진행 중인 이벤트에는 응모된다")
        void openEventAcceptsEntry() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .header("Authorization", bearer(entrantToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "응모합니다"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("시작 전 이벤트에는 응모할 수 없다 — 화면에서 버튼을 감춰도 API 는 열려 있다")
        void upcomingEventRejectsEntry() throws Exception {
            long id = createEvent("예정 이벤트", +1, +2);

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .header("Authorization", bearer(entrantToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "미리 응모"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("종료된 이벤트에는 응모할 수 없다 — 종료 직전에 열어 둔 화면에서 눌러도 막힌다")
        void closedEventRejectsEntry() throws Exception {
            long id = createEvent("종료 이벤트", -2, -1);

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .header("Authorization", bearer(entrantToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "늦은 응모"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("로그인 없이 응모할 수 없다")
        void anonymousCannotEnter() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "익명 응모"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =================================================================
    // 1인 1회
    // =================================================================

    @Nested
    @DisplayName("중복 응모")
    class DuplicateEntry {

        @Test
        @DisplayName("같은 사람은 두 번 응모할 수 없다 — 막지 않으면 이벤트가 성립하지 않는다")
        void sameUserCannotEnterTwice() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);
            enter(id, entrantToken, "첫 응모");

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .header("Authorization", bearer(entrantToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "두 번째 응모"))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("다른 사람은 응모된다 — 막는 것은 중복이지 참여가 아니다")
        void otherUserCanEnter() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);
            enter(id, entrantToken, "첫 응모");

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .header("Authorization", bearer(strangerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "다른 사람 응모"))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("취소한 뒤에는 다시 응모된다 — 취소가 참여 자격까지 없애지는 않는다")
        void canReenterAfterWithdraw() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);
            long entryId = enter(id, entrantToken, "첫 응모");

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/v1/events/entries/{entryId}", entryId)
                            .header("Authorization", bearer(entrantToken)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/events/{id}/entries", id)
                            .header("Authorization", bearer(entrantToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "다시 응모"))))
                    .andExpect(status().isCreated());
        }
    }

    // =================================================================
    // 응모 소유권 · 이름 가리기
    // =================================================================

    @Nested
    @DisplayName("응모 조회와 취소")
    class EntryOwnership {

        @Test
        @DisplayName("남의 응모는 취소할 수 없다 — 존재를 알리지 않는 404 다")
        void strangerCannotWithdraw() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);
            long entryId = enter(id, entrantToken, "내 응모");

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/v1/events/entries/{entryId}", entryId)
                            .header("Authorization", bearer(strangerToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("응모자명이 가려져 나온다 — 문의게시판과 같은 규칙이다")
        void entrantNameIsMasked() throws Exception {
            long id = createEvent("진행 중 이벤트", -1, +1);
            enter(id, entrantToken, "내 응모");

            MvcResult result = mockMvc.perform(get("/api/v1/events/{id}/entries", id))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(body(result)).contains("김**").doesNotContain("김철수");
        }
    }

    // =================================================================
    // 공개 노출
    // =================================================================

    @Nested
    @DisplayName("공개 목록과 상세")
    class PublicVisibility {

        @Test
        @DisplayName("이벤트 목록은 로그인 없이 읽힌다")
        void listIsPublic() throws Exception {
            createEvent("진행 중 이벤트", -1, +1);

            mockMvc.perform(get("/api/v1/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("내린 이벤트는 목록에 섞이지 않고 상세는 404 다")
        void hiddenEventDisappears() throws Exception {
            long id = createEvent("내릴 이벤트", -1, +1);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/admin/events/{id}", id)
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/events"))
                    .andExpect(jsonPath("$.content.length()").value(0));
            mockMvc.perform(get("/api/v1/events/{id}", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("내려도 행이 지워지지 않는다 — 무엇을 걸었는지가 남아야 한다")
        void hideKeepsTheRow() throws Exception {
            long id = createEvent("내릴 이벤트", -1, +1);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/admin/events/{id}", id)
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isNoContent());

            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM events WHERE id = ?", Integer.class, id);
            assertThat(rows).isEqualTo(1);
        }

        @Test
        @DisplayName("토큰 없이 이벤트를 만들 수 없다")
        void createRequiresAdmin() throws Exception {
            mockMvc.perform(post("/api/admin/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(eventBody("무단 이벤트", -1, +1))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    /** 지금으로부터 {@code startDays}~{@code endDays} 일 구간의 이벤트를 만든다. */
    private long createEvent(String title, int startDays, int endDays) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(eventBody(title, startDays, endDays))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    private Map<String, Object> eventBody(String title, int startDays, int endDays) {
        DateTimeFormatter iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", title + " 본문");
        body.put("startAt", OffsetDateTime.now().plusDays(startDays).format(iso));
        body.put("endAt", OffsetDateTime.now().plusDays(endDays).format(iso));
        return body;
    }

    private long enter(long eventId, String token, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/events/{id}/entries", eventId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", content))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    /** 소셜 가입을 끝내고 회원 Access 토큰을 돌려준다. 본인확인 단계까지 포함한다. */
    private String signUp(String providerUserId, String email, String name) throws Exception {
        return flow.social(providerUserId, email, name);
    }

    private String adminLogin(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId,
                                "password", AdminTestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
