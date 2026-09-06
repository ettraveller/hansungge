package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubMailConfig;
import com.hanseongge.support.StubMailSender;
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
 * 본인확인 공급자가 <b>없는</b> 기간의 가입 — 계약(TODO E-4) 전의 실제 운영 상태다.
 *
 * <p>{@code StubIdentityConfig} 를 <b>일부러 넣지 않는다.</b> 운영용 공급자는 기본
 * 비활성이고 개발용 목은 {@code local}/{@code dev} 전용이라, 이 설정이 곧 계약 전
 * 운영 서버의 모습이다.
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 * 본인확인을 모든 가입에 강제하도록 만들었더니 <b>계약이 끝날 때까지 배포를 할 수
 * 없게 됐다.</b> 이미 되던 소셜 로그인까지 막히기 때문이다. 그건 설계 실수였고,
 * 고친 뒤에는 "공급자가 없을 때 소셜이 실제로 통과하는가"가 배포 가능 여부를
 * 결정하는 조건이 된다. 그래서 따로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class SignUpWithoutIdentityProviderIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;
    @Autowired StubMailSender mailSender;

    @BeforeEach
    void setUp() {
        google.reset();
        mailSender.reset();
        UserTestFixtures.deleteAll(jdbc);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("소셜 가입이 그대로 된다 — 계약을 기다리느라 되던 로그인을 막지 않는다")
    void socialSignUpStillWorks() throws Exception {
        google.willReturn("google-1", "buyer@test.local", "김철수");

        String state = read(mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/authorize"))
                .andExpect(status().isOk()).andReturn()).get("state").asText();

        MvcResult callback = mockMvc.perform(post("/api/v1/auth/oauth/GOOGLE/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "code-1", "state", state))))
                .andExpect(status().isOk())
                .andReturn();

        // 본인확인 단계를 요구하지 않는다.
        assertThat(read(callback).get("signUpRequired").asBoolean()).isTrue();
        assertThat(read(callback).hasNonNull("identityRequired")).isFalse();

        MvcResult signedUp = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "signUpToken", read(callback).get("signUpToken").asText(),
                                "agreements", SignUpFlow.allRequiredAgreed()))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(read(signedUp).hasNonNull("accessToken")).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);

        // 이렇게 만든 회원은 본인확인을 거치지 않았다. 그 사실이 DB 에 남는다.
        assertThat(jdbc.queryForObject("SELECT di FROM users LIMIT 1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT phone_verified_at FROM users LIMIT 1", String.class))
                .isNull();
        // 이름은 소셜 제공자가 준 값을 쓴다.
        assertThat(jdbc.queryForObject("SELECT name FROM users LIMIT 1", String.class))
                .isEqualTo("김철수");
    }

    @Test
    @DisplayName("자체 가입은 열리지 않는다 — 이름도 생년월일도 본인확인에서만 온다")
    void emailSignUpIsClosed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "buyer@test.local"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"));

        assertThat(mailSender.sent).isEmpty();
    }

    @Test
    @DisplayName("화면이 자체 가입 버튼을 그리지 않도록 서버가 알려준다")
    void screenIsToldEmailSignUpIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailSignUpAvailable").value(false))
                // 소셜은 그대로 쓸 수 있다.
                .andExpect(jsonPath("$.providers.length()").value(1));
    }
}
