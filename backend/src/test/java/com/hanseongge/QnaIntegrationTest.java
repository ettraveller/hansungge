package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.audit.AuditActions;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문의게시판 · 제품문의 (설계서 §5 콘텐츠).
 *
 * <p>이 게시판의 핵심은 글이 아니라 <b>누가 무엇을 볼 수 있는가</b>다. 목록은 공개인데
 * 본문은 아니고, 이름은 보이는데 실명은 아니다. 그 경계가 어긋나면 게시판이 개인정보
 * 유출 경로가 된다. 여기서 고정하는 것:
 *
 * <ul>
 *   <li><b>남의 비밀글은 404</b> — 403 이 아니다. 403 이면 "그 번호에 비밀글이 있다"가
 *       드러나고 아이디를 훑어 비밀 문의 수를 셀 수 있다. 없는 글과 구분되지 않아야 한다</li>
 *   <li><b>작성자명을 서버가 가린다</b> — 공개 응답에 원본 이름이 담기는 경로가 없다.
 *       화면에서 자르면 실명은 이미 응답에 실려 나간 뒤다</li>
 *   <li><b>두 게시판이 섞이지 않는다</b> — 제품문의 글이 문의게시판 목록에 새어 들어가면
 *       상품을 특정한 질문이 엉뚱한 곳에서 답변을 기다린다</li>
 *   <li><b>남의 글은 고칠 수도 지울 수도 없다</b> — 여기서도 404 다</li>
 *   <li><b>답변 후에는 질문이 바뀌지 않는다</b> — 답과 맞지 않는 질문이 남는다</li>
 * </ul>
 *
 * <p>실제 소셜 서버 대신 {@link StubOAuthProvider} 를 끼운다. 검증 대상은 구글의 동작이
 * 아니라 <b>우리 게시판의 접근 규칙</b>이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-qna-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000",
        "spring.main.allow-bean-definition-overriding=true"
})
class QnaIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StubOAuthProvider google;
    @Autowired StubIdentityVerificationProvider identityProvider;

    /** 가입 4단계를 한 줄로 태운다. 단계별 검증이 목적인 테스트는 쓰지 않는다. */
    private SignUpFlow flow;

    /** 글을 쓰는 사람. 이름이 "김철수"라 마스킹 결과가 "김**" 이어야 한다. */
    private String authorToken;
    /** 남의 글을 들여다보는 사람. */
    private String strangerToken;
    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        google.reset();
        identityProvider.reset();
        flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);

        authorToken = signUp("author-1", "author@test.local", "김철수");
        strangerToken = signUp("stranger-1", "stranger@test.local", "박영희");

        AdminUser manager = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "qnamgr", AdminRole.MANAGER);
        managerToken = adminLogin(manager.getLoginId());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM qnas");
        jdbc.update("DELETE FROM audit_logs");
        UserTestFixtures.deleteAll(jdbc);
        jdbc.update("DELETE FROM admin_users WHERE login_id LIKE ?", AdminTestFixtures.PREFIX + "%");
    }

    // =================================================================
    // 비밀글 — 이 게시판에서 가장 중요한 경계
    // =================================================================

    @Nested
    @DisplayName("비밀글 접근")
    class SecretAccess {

        @Test
        @DisplayName("남의 비밀글을 열면 404 — 403 이면 그 번호에 글이 있다는 사실이 새어 나간다")
        void strangerGetsNotFound() throws Exception {
            long id = write(authorToken, "GENERAL", "비밀 문의", true, null);

            mockMvc.perform(get("/api/v1/qnas/{id}", id).header("Authorization", bearer(strangerToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("없는 글과 남의 비밀글이 같은 응답이다 — 둘을 구분할 수 있으면 존재를 훑을 수 있다")
        void missingAndForbiddenAreIndistinguishable() throws Exception {
            long id = write(authorToken, "GENERAL", "비밀 문의", true, null);

            MvcResult secret = mockMvc.perform(get("/api/v1/qnas/{id}", id)
                            .header("Authorization", bearer(strangerToken)))
                    .andExpect(status().isNotFound())
                    .andReturn();

            MvcResult missing = mockMvc.perform(get("/api/v1/qnas/{id}", 999_999L)
                            .header("Authorization", bearer(strangerToken)))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(read(secret).get("code").asText())
                    .isEqualTo(read(missing).get("code").asText());
        }

        @Test
        @DisplayName("비로그인도 남의 비밀글을 열 수 없다")
        void anonymousCannotOpenSecret() throws Exception {
            long id = write(authorToken, "GENERAL", "비밀 문의", true, null);

            mockMvc.perform(get("/api/v1/qnas/{id}", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("작성자 본인은 자기 비밀글을 연다 — mine 이 true 다")
        void ownerOpensOwnSecret() throws Exception {
            long id = write(authorToken, "GENERAL", "비밀 문의", true, null);

            mockMvc.perform(get("/api/v1/qnas/{id}", id).header("Authorization", bearer(authorToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.question").value("비밀 문의 본문"))
                    .andExpect(jsonPath("$.mine").value(true));
        }

        @Test
        @DisplayName("관리자는 비밀글을 본다 — 답변을 달아야 하기 때문이다")
        void adminOpensSecret() throws Exception {
            long id = write(authorToken, "GENERAL", "비밀 문의", true, null);

            mockMvc.perform(get("/api/admin/qnas/{id}", id).header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.question").value("비밀 문의 본문"));
        }

        @Test
        @DisplayName("비밀글도 목록에는 제목이 나온다 — 답변을 기다리는 글이 몇 건인지는 공개다")
        void secretStillAppearsInList() throws Exception {
            write(authorToken, "GENERAL", "비밀 문의", true, null);

            mockMvc.perform(get("/api/v1/qnas").param("boardType", "GENERAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("비밀 문의"))
                    .andExpect(jsonPath("$.content[0].secret").value(true));
        }

        @Test
        @DisplayName("공개글은 비로그인도 본문까지 읽는다 — 이 게시판을 두는 이유가 그것이다")
        void publicPostIsReadableByAnyone() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);

            mockMvc.perform(get("/api/v1/qnas/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.question").value("배송 문의 본문"))
                    .andExpect(jsonPath("$.mine").value(false));
        }
    }

    // =================================================================
    // 작성자명 — 가리는 일을 서버가 한다
    // =================================================================

    @Nested
    @DisplayName("작성자명 마스킹")
    class AuthorMasking {

        @Test
        @DisplayName("목록의 작성자명이 가려져 나온다 — 김철수 → 김**")
        void listMasksAuthorName() throws Exception {
            write(authorToken, "GENERAL", "배송 문의", false, null);

            mockMvc.perform(get("/api/v1/qnas").param("boardType", "GENERAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].authorName").value("김**"));
        }

        @Test
        @DisplayName("공개 응답 어디에도 원본 이름이 없다 — 화면이 아니라 응답에서 사라져야 한다")
        void rawNameNeverLeavesTheServer() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);

            MvcResult list = mockMvc.perform(get("/api/v1/qnas").param("boardType", "GENERAL"))
                    .andExpect(status().isOk())
                    .andReturn();
            MvcResult detail = mockMvc.perform(get("/api/v1/qnas/{id}", id))
                    .andExpect(status().isOk())
                    .andReturn();

            // 본문(raw JSON)에 실명이 들어 있으면 화면에서 가려도 소용없다
            assertThat(body(list)).doesNotContain("김철수");
            assertThat(body(detail)).doesNotContain("김철수");
        }

        @Test
        @DisplayName("관리자 목록에는 원본 이름이 온다 — 누가 물었는지 알아야 응대한다")
        void adminSeesRealName() throws Exception {
            write(authorToken, "GENERAL", "배송 문의", false, null);

            MvcResult result = mockMvc.perform(get("/api/admin/qnas")
                            .param("boardType", "GENERAL")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(body(result)).contains("김철수");
        }
    }

    // =================================================================
    // 두 게시판 분리 — board_type 을 둔 이유
    // =================================================================

    @Nested
    @DisplayName("문의게시판 · 제품문의 분리")
    class BoardSeparation {

        @Test
        @DisplayName("제품문의 글이 문의게시판 목록에 섞이지 않는다")
        void productQnaDoesNotLeakIntoGeneral() throws Exception {
            long productId = insertProduct();
            write(authorToken, "GENERAL", "일반 문의", false, null);
            write(authorToken, "PRODUCT", "제품 문의", false, productId);

            mockMvc.perform(get("/api/v1/qnas").param("boardType", "GENERAL"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("일반 문의"));

            mockMvc.perform(get("/api/v1/qnas").param("boardType", "PRODUCT"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("제품 문의"));
        }

        @Test
        @DisplayName("없는 상품으로 제품문의를 쓸 수 없다 — 어느 상품인지 알 수 없는 글이 남는다")
        void cannotAttachMissingProduct() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("boardType", "PRODUCT");
            body.put("title", "제품 문의");
            body.put("question", "본문");
            body.put("productId", 999_999L);

            mockMvc.perform(post("/api/v1/qnas")
                            .header("Authorization", bearer(authorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isNotFound());
        }
    }

    // =================================================================
    // 작성자 권한
    // =================================================================

    @Nested
    @DisplayName("수정 · 삭제")
    class OwnerOnly {

        @Test
        @DisplayName("남의 글은 수정할 수 없다 — 여기서도 404 다")
        void strangerCannotUpdate() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("boardType", "GENERAL");
            body.put("title", "바뀐 제목");
            body.put("question", "바뀐 본문");

            mockMvc.perform(put("/api/v1/qnas/{id}", id)
                            .header("Authorization", bearer(strangerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("남의 글은 지울 수 없다")
        void strangerCannotDelete() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);

            mockMvc.perform(delete("/api/v1/qnas/{id}", id)
                            .header("Authorization", bearer(strangerToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("답변이 달린 뒤에는 질문을 고칠 수 없다 — 답과 맞지 않는 질문이 남는다")
        void cannotEditAfterAnswer() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);
            answer(id, "3일 걸립니다.");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("boardType", "GENERAL");
            body.put("title", "사실은 다른 질문");
            body.put("question", "환불 되나요");

            mockMvc.perform(put("/api/v1/qnas/{id}", id)
                            .header("Authorization", bearer(authorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("로그인 없이 글을 쓸 수 없다")
        void anonymousCannotWrite() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("boardType", "GENERAL");
            body.put("title", "제목");
            body.put("question", "본문");

            mockMvc.perform(post("/api/v1/qnas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =================================================================
    // 답변
    // =================================================================

    @Nested
    @DisplayName("관리자 답변")
    class Answering {

        @Test
        @DisplayName("답변하면 상태가 ANSWERED 로 바뀌고 공개 상세에 답이 실린다")
        void answerBecomesVisible() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);
            answer(id, "3일 걸립니다.");

            mockMvc.perform(get("/api/v1/qnas/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ANSWERED"))
                    .andExpect(jsonPath("$.answer").value("3일 걸립니다."));
        }

        @Test
        @DisplayName("답변은 감사로그에 남는다 — 누가 무엇이라 답했는지가 분쟁의 근거다")
        void answerIsAudited() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);
            int before = countAudit(AuditActions.QNA_ANSWER);

            answer(id, "3일 걸립니다.");

            assertThat(countAudit(AuditActions.QNA_ANSWER)).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("토큰 없이 답변할 수 없다")
        void answerRequiresAdmin() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);

            mockMvc.perform(put("/api/admin/qnas/{id}/answer", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("answer", "아무나 답변"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("회원 토큰으로 관리자 답변 API 를 부를 수 없다 — aud 가 다르다")
        void userTokenCannotAnswer() throws Exception {
            long id = write(authorToken, "GENERAL", "배송 문의", false, null);

            mockMvc.perform(put("/api/admin/qnas/{id}/answer", id)
                            .header("Authorization", bearer(authorToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("answer", "회원이 단 답변"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    /** 소셜 가입까지 끝내고 회원 Access 토큰을 돌려준다. */
    /** 소셜 가입을 끝내고 회원 Access 토큰을 돌려준다. 본인확인 단계까지 포함한다. */
    private String signUp(String providerUserId, String email, String name) throws Exception {
        return flow.social(providerUserId, email, name);
    }

    private long write(String token, String boardType, String title, boolean secret, Long productId)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("boardType", boardType);
        body.put("title", title);
        body.put("question", title + " 본문");
        body.put("secret", secret);
        if (productId != null) {
            body.put("productId", productId);
        }

        MvcResult result = mockMvc.perform(post("/api/v1/qnas")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return read(result).get("id").asLong();
    }

    private void answer(long id, String answer) throws Exception {
        mockMvc.perform(put("/api/admin/qnas/{id}/answer", id)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("answer", answer))))
                .andExpect(status().isOk());
    }

    /**
     * 제품문의를 붙일 상품 한 건.
     *
     * <p>상품 등록 API 를 거치지 않고 직접 넣는다 — 여기서 검증하려는 것은 상품 등록이
     * 아니라 문의의 게시판 분리다.
     */
    private long insertProduct() {
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO products (category_id, name, metal_type, price_type, weight_g,
                                      labor_cost, status, active, created_by, updated_by)
                VALUES (?, '테스트 골드바', 'GOLD_24K', 'MARKET_LINKED', 3.75, 50000,
                        'ON_SALE', true, 'test', 'test')
                """, categoryId);
        return jdbc.queryForObject(
                "SELECT id FROM products ORDER BY id DESC LIMIT 1", Long.class);
    }

    private int countAudit(String action) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
        return count == null ? 0 : count;
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
