package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.RefreshToken;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import jakarta.servlet.http.Cookie;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 소셜 로그인·가입 (STEP 9, 개발계획 §7).
 *
 * <p>실제 제공자 서버 대신 {@link StubOAuthProvider} 를 끼운다. 검증하려는 것은 구글의
 * 응답 형식이 아니라 <b>우리 쪽 흐름</b>이다 — 약관 동의 전에 계정을 만들지 않는가,
 * 토큰이 회전하는가, 관리자 토큰과 섞이지 않는가.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubOAuthConfig.class)
@TestPropertySource(properties = {
        // 스텁 제공자가 컴포넌트 스캔이 등록한 실물 빈을 덮어쓴다.
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class UserAuthIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;

    @BeforeEach
    void setUp() {
        google.reset();
        cleanUp();
    }

    /**
     * 회원 관련 데이터를 걷어낸다.
     *
     * <p>{@code user_agreements} 는 append-only 트리거가 DELETE 를 막으므로 잠시 내린다.
     * 테스트에서만 하는 일이고, 애플리케이션에는 이 테이블을 지우는 경로가 없다.
     */
    private void cleanUp() {
        jdbc.execute("ALTER TABLE user_agreements DISABLE TRIGGER trg_user_agreement_guard");
        try {
            jdbc.update("DELETE FROM user_agreements");
            jdbc.update("DELETE FROM refresh_tokens");
            jdbc.update("DELETE FROM social_accounts");
            jdbc.update("DELETE FROM users");
        } finally {
            jdbc.execute("ALTER TABLE user_agreements ENABLE TRIGGER trg_user_agreement_guard");
        }
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

    /** 인가 요청 → 유효한 state 확보. */
    private String authorizeState() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/authorize"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("state").asText();
    }

    private MvcResult callback(String code, String state) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "state", state))))
                .andReturn();
    }

    /** 필수 약관 전부 동의 + 마케팅 거부. */
    private List<Map<String, Object>> allRequiredAgreed() {
        return List.of(
                Map.of("type", "TERMS", "agreed", true),
                Map.of("type", "PRIVACY", "agreed", true),
                Map.of("type", "AGE_14", "agreed", true),
                Map.of("type", "MARKETING", "agreed", false));
    }

    /** 가입까지 끝내고 {accessToken, refreshCookie} 를 돌려준다. */
    private Session signUpNewUser() throws Exception {
        MvcResult cb = callback("code-1", authorizeState());
        assertThat(cb.getResponse().getStatus()).isEqualTo(200);
        String signUpToken = read(cb).get("signUpToken").asText();

        MvcResult signedUp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "signUpToken", signUpToken,
                                "agreements", allRequiredAgreed()))))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(read(signedUp).get("accessToken").asText(),
                signedUp.getResponse().getCookie("hsg_rt"));
    }

    private record Session(String accessToken, Cookie refreshCookie) {}

    // =================================================================
    // 제공자 노출
    // =================================================================

    @Nested
    @DisplayName("로그인 화면 구성")
    class Providers {

        @Test
        @DisplayName("설정된 제공자만 노출된다 — 앱 검수 전 제공자가 버튼으로 뜨면 안 된다")
        void onlyConfiguredProvidersAreListed() throws Exception {
            mockMvc.perform(get("/api/v1/auth/providers"))
                    .andExpect(status().isOk())
                    // 네이버·카카오는 클라이언트 설정이 없어 목록에서 빠진다
                    .andExpect(jsonPath("$.providers.length()").value(1))
                    .andExpect(jsonPath("$.providers[0].code").value("GOOGLE"))
                    .andExpect(jsonPath("$.agreements.length()").value(5));
        }

        @Test
        @DisplayName("약관 목록에 필수 여부와 버전이 함께 온다")
        void agreementsCarryRequiredFlagAndVersion() throws Exception {
            mockMvc.perform(get("/api/v1/auth/providers"))
                    .andExpect(status().isOk())
                    // 필수가 앞에 온다
                    .andExpect(jsonPath("$.agreements[0].required").value(true))
                    .andExpect(jsonPath("$.agreements[0].version").value("1.0"));
        }

        @Test
        @DisplayName("설정되지 않은 제공자로 로그인을 시작하면 503 — 없는 것이 아니라 준비되지 않은 것이다")
        void unconfiguredProviderReturns503() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/NAVER/authorize"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("OAUTH_PROVIDER_UNAVAILABLE"));
        }

        @Test
        @DisplayName("인가 URL 과 교환에 같은 redirect_uri 를 쓴다 — 다르면 제공자가 거부한다")
        void redirectUriIsConsistent() throws Exception {
            authorizeState();
            String fromAuthorize = google.lastRedirectUri;
            assertThat(fromAuthorize).isEqualTo("http://localhost:3000/auth/callback/google");

            callback("code-1", authorizeState());
            assertThat(google.lastRedirectUri).isEqualTo(fromAuthorize);
        }
    }

    // =================================================================
    // 가입
    // =================================================================

    @Nested
    @DisplayName("가입 — 약관 동의 전에는 계정을 만들지 않는다")
    class SignUp {

        @Test
        @DisplayName("처음 오는 소셜 계정이면 가입 대기 상태로 돌려주고, 회원 행은 아직 없다")
        void firstTimeReturnsSignUpTokenWithoutCreatingAccount() throws Exception {
            MvcResult result = callback("code-1", authorizeState());

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode body = read(result);
            assertThat(body.get("signUpRequired").asBoolean()).isTrue();
            assertThat(body.get("signUpToken").asText()).isNotBlank();
            assertThat(body.has("accessToken")).isFalse();
            assertThat(body.get("suggestedEmail").asText()).isEqualTo("buyer@test.local");

            // 핵심: 동의 전에는 어떤 개인정보도 저장되지 않는다
            assertThat(count("users")).isZero();
            assertThat(count("social_accounts")).isZero();
            assertThat(count("user_agreements")).isZero();
            // Refresh 쿠키도 나가지 않는다
            assertThat(result.getResponse().getCookie("hsg_rt")).isNull();
        }

        @Test
        @DisplayName("필수 약관이 빠지면 400 이고, 어떤 약관이 빠졌는지 알려준다")
        void missingMandatoryAgreementIsRejected() throws Exception {
            String signUpToken = read(callback("code-1", authorizeState())).get("signUpToken").asText();

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "signUpToken", signUpToken,
                                    "agreements", List.of(
                                            Map.of("type", "TERMS", "agreed", true),
                                            Map.of("type", "PRIVACY", "agreed", true),
                                            Map.of("type", "AGE_14", "agreed", false))))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGREEMENT_REQUIRED"))
                    .andExpect(jsonPath("$.errors.AGE_14").exists());

            assertThat(count("users")).isZero();
        }

        @Test
        @DisplayName("가입하면 회원·소셜연동·동의이력이 함께 생기고 세션이 발급된다")
        void signUpCreatesEverythingInOneGo() throws Exception {
            Session session = signUpNewUser();

            assertThat(session.accessToken()).isNotBlank();
            assertThat(session.refreshCookie()).isNotNull();
            assertThat(session.refreshCookie().isHttpOnly())
                    .as("장기 토큰은 스크립트가 읽을 수 없어야 한다")
                    .isTrue();
            assertThat(session.refreshCookie().getPath()).isEqualTo("/api/v1/auth");

            assertThat(count("users")).isEqualTo(1);
            assertThat(count("social_accounts")).isEqualTo(1);
            // 선택 약관까지 전부 기록한다 — "묻지 않은 것"과 "거절한 것"은 다르다
            assertThat(count("user_agreements")).isEqualTo(AgreementType.values().length);

            Boolean marketing = jdbc.queryForObject(
                    "SELECT agreed FROM user_agreements WHERE agreement_type = 'MARKETING'",
                    Boolean.class);
            assertThat(marketing).isFalse();
        }

        @Test
        @DisplayName("Refresh 토큰 원문은 DB 에 없다 — 해시만 저장된다")
        void refreshTokenIsStoredAsHashOnly() throws Exception {
            Session session = signUpNewUser();
            String raw = session.refreshCookie().getValue();

            String storedHash = jdbc.queryForObject(
                    "SELECT token_hash FROM refresh_tokens WHERE subject_type = 'USER'", String.class);
            assertThat(storedHash).isNotEqualTo(raw);
            assertThat(storedHash).isEqualTo(RefreshToken.hash(raw));
        }

        @Test
        @DisplayName("만료·위조된 가입 토큰은 거부된다")
        void tamperedSignUpTokenIsRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "signUpToken", "eyJhbGciOiJIUzI1NiJ9.fake.signature",
                                    "agreements", allRequiredAgreed()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
        }
    }

    // =================================================================
    // 로그인
    // =================================================================

    @Nested
    @DisplayName("로그인")
    class SignIn {

        @Test
        @DisplayName("이미 연동된 소셜 계정이면 가입 없이 바로 로그인된다")
        void existingSocialSignsInDirectly() throws Exception {
            signUpNewUser();

            MvcResult again = callback("code-2", authorizeState());
            JsonNode body = read(again);

            assertThat(body.get("signUpRequired").asBoolean()).isFalse();
            assertThat(body.get("accessToken").asText()).isNotBlank();
            assertThat(body.get("user").get("email").asText()).isEqualTo("buyer@test.local");
            // 계정이 새로 생기지 않는다
            assertThat(count("users")).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 이메일이라도 제공자 식별자가 다르면 별도 계정이다 (§7.2)")
        void sameEmailDifferentProviderUserIsSeparateAccount() throws Exception {
            signUpNewUser();

            // 이메일은 같고 제공자 식별자만 다른 사람
            google.willReturn("stub-user-2", "buyer@test.local", "다른 사람");
            JsonNode body = read(callback("code-3", authorizeState()));

            assertThat(body.get("signUpRequired").asBoolean())
                    .as("이메일로 기존 계정에 흡수되면 안 된다")
                    .isTrue();
        }

        @Test
        @DisplayName("state 가 없거나 다른 제공자의 것이면 거부된다 — 인가코드 주입 방지")
        void invalidStateIsRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("code", "code-1", "state", "not-a-real-state"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OAUTH_STATE_INVALID"));
        }

        @Test
        @DisplayName("제공자가 실패하면 502 로 바꿔 내보낸다 — 원문을 그대로 노출하지 않는다")
        void providerFailureBecomesGatewayError() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("code", "bad-code", "state", authorizeState()))))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("OAUTH_EXCHANGE_FAILED"));
        }
    }

    // =================================================================
    // 세션
    // =================================================================

    @Nested
    @DisplayName("세션 유지와 종료")
    class SessionLifecycle {

        @Test
        @DisplayName("재발급하면 Refresh 토큰도 회전하고, 이전 토큰은 즉시 무효가 된다")
        void refreshRotatesToken() throws Exception {
            Session session = signUpNewUser();

            MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(session.refreshCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();

            Cookie rotated = refreshed.getResponse().getCookie("hsg_rt");
            assertThat(rotated).isNotNull();
            assertThat(rotated.getValue()).isNotEqualTo(session.refreshCookie().getValue());

            // 새 토큰은 동작하고
            mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("폐기된 Refresh 토큰이 다시 오면 그 회원의 세션을 전부 끊는다 — 탈취 신호로 본다")
        void reusedRefreshTokenRevokesAllSessions() throws Exception {
            Session session = signUpNewUser();
            MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(session.refreshCookie()))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie rotated = refreshed.getResponse().getCookie("hsg_rt");

            // 이미 회전으로 폐기된 옛 토큰을 다시 쓴다
            mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                    .andExpect(status().isUnauthorized());

            // 정상 발급됐던 새 토큰까지 함께 끊긴다
            mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("로그아웃하면 쿠키가 지워지고 그 토큰은 더 이상 쓸 수 없다")
        void logoutClearsCookieAndRevokes() throws Exception {
            Session session = signUpNewUser();

            MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(session.refreshCookie()))
                    .andExpect(status().isNoContent())
                    .andReturn();

            Cookie cleared = result.getResponse().getCookie("hsg_rt");
            assertThat(cleared).isNotNull();
            assertThat(cleared.getMaxAge()).isZero();

            mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("쿠키 없이 로그아웃해도 성공한다 — 로그아웃이 실패하면 안 된다")
        void logoutWithoutCookieStillSucceeds() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isNoContent());
        }
    }

    // =================================================================
    // 토큰 분리
    // =================================================================

    @Nested
    @DisplayName("관리자 토큰과 회원 토큰은 섞이지 않는다 (§7.1)")
    class TokenSeparation {

        @Test
        @DisplayName("회원 토큰으로 관리자 API 를 부를 수 없다")
        void userTokenCannotAccessAdminApi() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(get("/api/admin/prices/current")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("토큰 없이 마이페이지에 접근하면 401")
        void myPageRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("회원 토큰으로 마이페이지에 접근할 수 있다")
        void userTokenAccessesMyPage() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(get("/api/v1/me")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("buyer@test.local"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    // =================================================================
    // 마이페이지
    // =================================================================

    @Nested
    @DisplayName("마이페이지")
    class MyPage {

        @Test
        @DisplayName("마케팅 동의를 철회하면 기존 행을 고치지 않고 새 행이 쌓인다 (D-9)")
        void withdrawingConsentAppendsRow() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(patch("/api/v1/me/agreements")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("type", "MARKETING", "agreed", true))))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/me/agreements")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("type", "MARKETING", "agreed", false))))
                    .andExpect(status().isOk());

            List<Boolean> history = jdbc.queryForList(
                    "SELECT agreed FROM user_agreements WHERE agreement_type = 'MARKETING' ORDER BY id",
                    Boolean.class);
            assertThat(history).containsExactly(false, true, false);
        }

        @Test
        @DisplayName("필수 약관은 철회할 수 없다 — 그것은 동의 철회가 아니라 탈퇴다")
        void mandatoryAgreementCannotBeWithdrawn() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(patch("/api/v1/me/agreements")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("type", "TERMS", "agreed", false))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("마지막 소셜 연동은 해제할 수 없다 — 로그인 수단이 사라진다")
        void lastSocialConnectionCannotBeRemoved() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/v1/me/social-accounts/GOOGLE")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SOCIAL_LAST_CONNECTION"));
        }

        @Test
        @DisplayName("탈퇴하면 회원 행은 남고 상태만 바뀌며, 이후 토큰이 거부된다")
        void withdrawKeepsRowAndBlocksToken() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(post("/api/v1/me/withdraw")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isNoContent());

            // 주문·결제가 회원을 참조하므로 행을 지우지 않는다
            assertThat(count("users")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM users", String.class))
                    .isEqualTo("WITHDRAWN");

            mockMvc.perform(get("/api/v1/me")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
        }

        @Test
        @DisplayName("내 정보를 수정할 수 있다")
        void updateProfile() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(patch("/api/v1/me")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "김구매", "phone", "010-1234-5678"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("김구매"))
                    .andExpect(jsonPath("$.phone").value("010-1234-5678"));
        }

        @Test
        @DisplayName("연락처 형식이 틀리면 400")
        void invalidPhoneIsRejected() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(patch("/api/v1/me")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "김구매", "phone", "전화번호"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.phone").exists());
        }
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
