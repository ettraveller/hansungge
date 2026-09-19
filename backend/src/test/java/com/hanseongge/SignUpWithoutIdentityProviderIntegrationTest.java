package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본인확인 공급자가 <b>없는</b> 기간 — 이니시스 계약 전 운영 서버의 모습이다.
 *
 * <p>{@code StubIdentityConfig} 를 <b>일부러 넣지 않는다.</b> {@code IDENTITY_PROVIDER} 가 비어
 * 있으면 어떤 공급자도 빈이 되지 않는다.
 *
 * <h2>무엇이 닫히고 무엇이 열려 있어야 하나</h2>
 * 모든 가입은 본인확인을 거친다. 공급자가 없으면 <b>가입이 닫힌다</b> — 자체·소셜 모두.
 * 대신 <b>기존 회원의 로그인은 그대로</b> 돼야 한다. 공급자 설정 실수 하나로 이미 가입한
 * 사람들이 통째로 못 들어오면, 가입이 막히는 것보다 훨씬 나쁘다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubOAuthConfig.class)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class SignUpWithoutIdentityProviderIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        google.reset();
        UserTestFixtures.deleteAll(jdbc);
    }

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

    @Test
    @DisplayName("화면이 가입 링크를 그리지 않도록 서버가 알려준다")
    void screenIsToldSignUpIsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signUpAvailable").value(false))
                // 소셜 로그인 버튼 자체는 그대로 — 기존 회원이 쓴다
                .andExpect(jsonPath("$.providers.length()").value(1));
    }

    @Test
    @DisplayName("자체 가입은 시작부터 막힌다")
    void selfSignUpIsClosed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("agreements", SignUpFlow.allRequiredAgreed()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SIGNUP_UNAVAILABLE"));
    }

    @Test
    @DisplayName("처음 보는 소셜 계정은 가입으로 넘어가지 않는다 — 본인확인 없이 회원을 만들지 않는다")
    void newSocialAccountIsNotSignedUp() throws Exception {
        google.willReturn("google-1", "buyer@test.local", "김철수");

        mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "code-1", "state", authorizeState()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SIGNUP_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    @DisplayName("이미 가입한 회원은 소셜로도, 휴대폰으로도 그대로 로그인된다")
    void existingMembersStillSignIn() throws Exception {
        var member = jdbc.queryForObject("""
                INSERT INTO users (name, phone, birth_date, di, phone_verified_at, password_hash, status)
                VALUES ('홍길동', '01012345678', '1990-01-01', 'DI-1', now(), ?, 'ACTIVE') RETURNING id
                """, Long.class, passwordEncoder.encode(SignUpFlow.PASSWORD));
        jdbc.update("INSERT INTO social_accounts (user_id, provider, provider_user_id) VALUES (?, 'GOOGLE', 'google-1')",
                member);

        google.willReturn("google-1", "buyer@test.local", "홍길동");
        MvcResult social = mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "code-1", "state", authorizeState()))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(read(social).get("accessToken").asText()).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "010-1234-5678", "password", SignUpFlow.PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호 찾기도 본인확인이 필요해 503 — 준비되지 않은 것이지 없는 것이 아니다")
    void passwordResetNeedsProvider() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset/identity/start"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"));
    }
}
