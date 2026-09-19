package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.RefreshToken;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.UserTestFixtures;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 로그인·가입, 세션, 마이페이지 (개발계획 §7).
 *
 * <p>실제 제공자 대신 스텁을 끼운다. 검증하려는 것은 구글·이니시스의 응답 형식이 아니라
 * <b>우리 쪽 흐름</b>이다 — 약관·본인확인 전에 계정을 만들지 않는가, 기존 회원에게 묻지 않고
 * 소셜을 붙이지 않는가, 토큰이 회전하는가, 관리자 토큰과 섞이지 않는가.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class})
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
    @Autowired StubIdentityVerificationProvider identityProvider;

    private SignUpFlow flow;

    @BeforeEach
    void setUp() {
        google.reset();
        identityProvider.reset();
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

    private String authorizeState() throws Exception {
        return read(mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/authorize"))
                .andExpect(status().isOk()).andReturn()).get("state").asText();
    }

    private MvcResult callback(String code, String state) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "state", state))))
                .andReturn();
    }

    /** 소셜 가입을 끝까지 — 콜백 → 약관 → 본인확인 → 가입 완료(SIGNED_IN). */
    private Session signUpNewUser() throws Exception {
        return signUpNewUser(UserTestFixtures.identity("김철수"));
    }

    private Session signUpNewUser(VerifiedIdentity identity) throws Exception {
        String callbackToken = read(callback("code-1", authorizeState())).get("signUpToken").asText();
        String agreed = flow.agree(callbackToken);
        identityProvider.willReturn(identity);
        MvcResult done = flow.verifyIdentityRaw(agreed);
        assertThat(done.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = read(done);
        assertThat(body.get("step").asText()).isEqualTo("SIGNED_IN");
        return new Session(body.get("session").get("accessToken").asText(),
                done.getResponse().getCookie("hsg_rt"));
    }

    private record Session(String accessToken, Cookie refreshCookie) {}

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    // =================================================================
    // 로그인 화면
    // =================================================================

    @Nested
    @DisplayName("로그인 화면 구성")
    class Providers {

        @Test
        @DisplayName("설정된 제공자만 노출된다 — 앱 검수 전 제공자가 버튼으로 뜨면 안 된다")
        void onlyConfiguredProvidersAreListed() throws Exception {
            mockMvc.perform(get("/api/v1/auth/providers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providers.length()").value(1))
                    .andExpect(jsonPath("$.providers[0].code").value("GOOGLE"))
                    .andExpect(jsonPath("$.agreements.length()").value(5))
                    .andExpect(jsonPath("$.signUpAvailable").value(true))
                    // 문자 대역을 넣지 않았다 — 운영 발송기는 설정이 비어 인증번호 로그인이 꺼진다
                    .andExpect(jsonPath("$.smsLoginAvailable").value(false));
        }

        @Test
        @DisplayName("약관 목록에 필수 여부와 버전이 함께 온다")
        void agreementsCarryRequiredFlagAndVersion() throws Exception {
            mockMvc.perform(get("/api/v1/auth/providers"))
                    .andExpect(status().isOk())
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
    // 소셜 가입
    // =================================================================

    @Nested
    @DisplayName("소셜 가입 — 약관 → 본인확인 → 가입. 그 전에는 아무것도 만들지 않는다")
    class SocialSignUp {

        @Test
        @DisplayName("처음 오는 소셜 계정이면 약관 목록과 진행 토큰만 주고, 회원 행은 아직 없다")
        void firstTimeReturnsSignUpTokenWithoutCreatingAccount() throws Exception {
            MvcResult result = callback("code-1", authorizeState());

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode body = read(result);
            assertThat(body.get("signUpRequired").asBoolean()).isTrue();
            assertThat(body.get("signUpToken").asText()).isNotBlank();
            assertThat(body.get("agreements").size()).isEqualTo(5);
            assertThat(body.has("accessToken")).isFalse();
            assertThat(body.get("suggestedEmail").asText()).isEqualTo("buyer@test.local");

            assertThat(count("users")).isZero();
            assertThat(count("social_accounts")).isZero();
            assertThat(count("user_agreements")).isZero();
            assertThat(result.getResponse().getCookie("hsg_rt")).isNull();
        }

        @Test
        @DisplayName("필수 약관이 빠지면 400 이고, 어떤 약관이 빠졌는지 알려준다")
        void missingMandatoryAgreementIsRejected() throws Exception {
            String callbackToken = read(callback("code-1", authorizeState())).get("signUpToken").asText();

            mockMvc.perform(post("/api/v1/auth/signup/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "signUpToken", callbackToken,
                                    "agreements", List.of(
                                            Map.of("type", "TERMS", "agreed", true),
                                            Map.of("type", "PRIVACY", "agreed", true),
                                            Map.of("type", "AGE_14", "agreed", false))))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGREEMENT_REQUIRED"))
                    .andExpect(jsonPath("$.errors.AGE_14").exists());
        }

        @Test
        @DisplayName("약관 동의 전에는 본인확인을 시작할 수 없다 — 개인정보 수집 전에 동의가 먼저다")
        void identityBeforeAgreementIsRejected() throws Exception {
            String callbackToken = read(callback("code-1", authorizeState())).get("signUpToken").asText();

            mockMvc.perform(post("/api/v1/auth/identity/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("signUpToken", callbackToken))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGREEMENT_REQUIRED"));
        }

        @Test
        @DisplayName("본인확인을 마치면 회원·소셜연동·동의이력이 함께 생기고 세션이 발급된다")
        void signUpCreatesEverythingInOneGo() throws Exception {
            Session session = signUpNewUser(UserTestFixtures.identityWith("DI-A", "김철수", "010-1234-5678"));

            assertThat(session.accessToken()).isNotBlank();
            assertThat(session.refreshCookie()).isNotNull();
            assertThat(session.refreshCookie().isHttpOnly())
                    .as("장기 토큰은 스크립트가 읽을 수 없어야 한다")
                    .isTrue();
            assertThat(session.refreshCookie().getPath()).isEqualTo("/api/v1/auth");

            assertThat(count("users")).isEqualTo(1);
            assertThat(count("social_accounts")).isEqualTo(1);
            assertThat(count("user_agreements")).isEqualTo(AgreementType.values().length);
            assertThat(jdbc.queryForObject(
                    "SELECT agreed FROM user_agreements WHERE agreement_type = 'MARKETING'", Boolean.class))
                    .isFalse();

            // 이름·휴대폰은 본인확인 값이다. 로그인 ID 는 숫자만.
            Map<String, Object> user = jdbc.queryForMap("SELECT name, phone, di, email FROM users");
            assertThat(user.get("name")).isEqualTo("김철수");
            assertThat(user.get("phone")).isEqualTo("01012345678");
            assertThat(user.get("di")).isEqualTo("DI-A");
            // 이메일은 소셜 제공자가 준 값
            assertThat(user.get("email")).isEqualTo("buyer@test.local");
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
        @DisplayName("위조된 진행 토큰은 거부된다")
        void tamperedSignUpTokenIsRejected() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "signUpToken", "eyJhbGciOiJIUzI1NiJ9.fake.signature",
                                    "agreements", SignUpFlow.allRequiredAgreed()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("소셜 제공자가 준 이메일을 다른 사람이 쓰고 있으면 이메일 없이 가입된다 — 이메일은 선택이다")
        void takenProviderEmailIsDropped() throws Exception {
            jdbc.update("INSERT INTO users (email, name, phone, birth_date, di, phone_verified_at, status) "
                    + "VALUES ('buyer@test.local', '다른사람', '01099998888', '1980-01-01', 'DI-OTHER', now(), 'ACTIVE')");

            signUpNewUser();

            assertThat(jdbc.queryForObject("SELECT email FROM users WHERE di <> 'DI-OTHER'", String.class))
                    .isNull();
        }
    }

    // =================================================================
    // 본인확인 결과 검증
    // =================================================================

    @Nested
    @DisplayName("본인확인 결과는 서버가 공급자에 되물어 확인한다")
    class IdentityResult {

        @Test
        @DisplayName("결과의 거래번호가 우리가 연 인증창과 다르면 거부된다 — 남의 결과를 내 가입에 붙이는 경로")
        void mismatchedRequestIdIsRejected() throws Exception {
            String agreed = flow.agree(read(callback("code-1", authorizeState())).get("signUpToken").asText());
            JsonNode started = read(mockMvc.perform(post("/api/v1/auth/identity/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("signUpToken", agreed))))
                    .andExpect(status().isOk()).andReturn());

            // 공급자가 보낸 값 중 거래번호를 다른 요청의 것으로 바꿔치기한다
            String forged = mockMvc.perform(post("/api/v1/auth/identity/return")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("resultCode", "0000")
                            .param("txId", "stub-forged")
                            .param("mTxId", "SomeoneElsesRequest0"))
                    .andExpect(status().isSeeOther())
                    .andReturn().getResponse().getHeader("Location");
            String providerToken = forged.substring(forged.indexOf("providerToken=") + "providerToken=".length());

            mockMvc.perform(post("/api/v1/auth/identity/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "token", started.get("token").asText(),
                                    "providerToken", java.net.URLDecoder.decode(providerToken,
                                            java.nio.charset.StandardCharsets.UTF_8)))))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));

            assertThat(count("users")).isZero();
        }

        @Test
        @DisplayName("수신 주소는 화면으로 303 을 보낸다 — 공급자의 폼 POST 를 화면이 받을 수 없어서다")
        void returnEndpointRedirectsToScreen() throws Exception {
            String location = mockMvc.perform(post("/api/v1/auth/identity/return")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("resultCode", "0000")
                            .param("txId", "stub-1"))
                    .andExpect(status().isSeeOther())
                    .andReturn().getResponse().getHeader("Location");

            assertThat(location).startsWith("http://localhost:3000/auth/identity?providerToken=");
        }
    }

    // =================================================================
    // 기존 회원에 소셜 연결
    // =================================================================

    @Nested
    @DisplayName("이미 가입한 사람이 소셜로 오면 — 묻고 나서 연결한다")
    class LinkToExisting {

        private VerifiedIdentity existing;

        @BeforeEach
        void existingPhoneMember() throws Exception {
            existing = UserTestFixtures.identityWith("DI-SAME", "김철수", "01011112222");
            flow.phone(existing, SignUpFlow.PASSWORD);
            assertThat(count("users")).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 DI 로 본인확인하면 CONFIRM_LINK — 기존 계정을 가려서 보여주고, 아직 아무것도 붙이지 않는다")
        void confirmLinkIsAskedFirst() throws Exception {
            String agreed = flow.agree(read(callback("code-1", authorizeState())).get("signUpToken").asText());
            identityProvider.willReturn(existing);
            JsonNode result = flow.verifyIdentity(agreed);

            assertThat(result.get("step").asText()).isEqualTo("CONFIRM_LINK");
            assertThat(result.get("accountPhone").asText()).isEqualTo("010-****-2222");
            assertThat(count("users")).isEqualTo(1);
            assertThat(count("social_accounts")).isZero();
        }

        @Test
        @DisplayName("[연결하기] 를 누르면 기존 계정에 소셜이 붙고 그 계정으로 로그인된다")
        void linkAttachesToExistingAccount() throws Exception {
            String agreed = flow.agree(read(callback("code-1", authorizeState())).get("signUpToken").asText());
            identityProvider.willReturn(existing);
            JsonNode result = flow.verifyIdentity(agreed);

            mockMvc.perform(post("/api/v1/auth/signup/link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("signUpToken", result.get("signUpToken").asText()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());

            assertThat(count("users")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM social_accounts s JOIN users u ON u.id = s.user_id WHERE u.di = 'DI-SAME'",
                    Integer.class)).isEqualTo(1);

            // 다음부터는 구글로 바로 들어온다
            JsonNode again = read(callback("code-2", authorizeState()));
            assertThat(again.get("signUpRequired").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("[취소] 하면 아무것도 만들지 않는다 — 새 계정을 만들면 중복가입 차단이 무너진다")
        void cancelCreatesNothing() throws Exception {
            String agreed = flow.agree(read(callback("code-1", authorizeState())).get("signUpToken").asText());
            identityProvider.willReturn(existing);
            flow.verifyIdentity(agreed);
            // 사용자가 연결 화면에서 취소 — 아무 요청도 보내지 않는다

            assertThat(count("users")).isEqualTo(1);
            assertThat(count("social_accounts")).isZero();
        }
    }

    // =================================================================
    // 소셜 로그인
    // =================================================================

    @Nested
    @DisplayName("소셜 로그인")
    class SignIn {

        @Test
        @DisplayName("이미 연동된 소셜 계정이면 가입 없이 바로 로그인된다")
        void existingSocialSignsInDirectly() throws Exception {
            signUpNewUser();

            JsonNode body = read(callback("code-2", authorizeState()));

            assertThat(body.get("signUpRequired").asBoolean()).isFalse();
            assertThat(body.get("accessToken").asText()).isNotBlank();
            assertThat(count("users")).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 이메일이라도 제공자 식별자가 다르면 이메일로 합치지 않는다 (§7.2)")
        void sameEmailDifferentProviderUserIsNotMerged() throws Exception {
            signUpNewUser();

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

            mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("폐기된 Refresh 토큰이 다시 오면 그 회원의 세션을 전부 끊는다 — 탈취 신호로 본다")
        void reusedRefreshTokenRevokesAllSessions() throws Exception {
            Session session = signUpNewUser();
            Cookie rotated = mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getCookie("hsg_rt");

            mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("로그아웃하면 쿠키가 지워지고 그 토큰은 더 이상 쓸 수 없다")
        void logoutClearsCookieAndRevokes() throws Exception {
            Session session = signUpNewUser();

            Cookie cleared = mockMvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                    .andExpect(status().isNoContent())
                    .andReturn().getResponse().getCookie("hsg_rt");
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

            for (boolean agreed : new boolean[]{true, false}) {
                mockMvc.perform(patch("/api/v1/me/agreements")
                                .header("Authorization", "Bearer " + session.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("type", "MARKETING", "agreed", agreed))))
                        .andExpect(status().isOk());
            }

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
        @DisplayName("비밀번호가 없으면 마지막 소셜 연동은 해제할 수 없다 — 로그인 수단이 사라진다")
        void lastSocialConnectionCannotBeRemoved() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(delete("/api/v1/me/social-accounts/GOOGLE")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SOCIAL_LAST_CONNECTION"));
        }

        @Test
        @DisplayName("소셜 가입자가 비밀번호를 정하면 휴대폰으로도 로그인되고, 소셜을 끊을 수 있다")
        void socialMemberSetsPassword() throws Exception {
            Session session = signUpNewUser(UserTestFixtures.identityWith("DI-P", "김철수", "01055556666"));

            mockMvc.perform(post("/api/v1/me/password")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("newPassword", SignUpFlow.PASSWORD))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("phone", "010-5555-6666", "password", SignUpFlow.PASSWORD))))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/v1/me/social-accounts/GOOGLE")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("비밀번호가 있으면 바꿀 때 현재 비밀번호를 확인한다")
        void changingPasswordRequiresCurrent() throws Exception {
            Session session = signUpNewUser();
            mockMvc.perform(post("/api/v1/me/password")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("newPassword", SignUpFlow.PASSWORD))))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/me/password")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("currentPassword", "Wrong-pass-1234!",
                                    "newPassword", "Another-pass-5678!"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        }

        @Test
        @DisplayName("이름은 고칠 수 없다 — 본인확인으로 확정된 값이고, 수정 API 자체가 없다")
        void nameCannotBeChanged() throws Exception {
            Session session = signUpNewUser();

            MvcResult result = mockMvc.perform(patch("/api/v1/me")
                            .header("Authorization", "Bearer " + session.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "김구매"))))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
            assertThat(jdbc.queryForObject("SELECT name FROM users", String.class)).isEqualTo("김철수");
        }

        @Test
        @DisplayName("탈퇴하면 회원 행은 남고 상태만 바뀌며, 이후 토큰이 거부된다")
        void withdrawKeepsRowAndBlocksToken() throws Exception {
            Session session = signUpNewUser();

            mockMvc.perform(post("/api/v1/me/withdraw")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isNoContent());

            assertThat(count("users")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM users", String.class))
                    .isEqualTo("WITHDRAWN");

            mockMvc.perform(get("/api/v1/me")
                            .header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
        }
    }
}
