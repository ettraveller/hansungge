package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 비밀번호 찾기 — 간편인증 → 그 자리에서 새 비밀번호.
 *
 * <p>임시 비밀번호를 문자로 보내지 않는다. 계정 선택 화면도 없다 — 같은 사람의 계정이 여럿이어도
 * 휴대폰 로그인이 되는 쪽 하나를 고른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class PasswordResetIntegrationTest extends EmbeddedPostgresSupport {

    private static final UserTestFixtures.Person PERSON =
            UserTestFixtures.personWith("CI-1", "홍길동", "01012345678");

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
        UserTestFixtures.deleteAll(jdbc);
        flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult reset(String resetToken, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("resetToken", resetToken, "newPassword", newPassword))))
                .andReturn();
    }

    private MvcResult login(String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "password", password))))
                .andReturn();
    }

    @Test
    @DisplayName("본인확인 → 새 비밀번호 → 새 비밀번호로 로그인, 옛 비밀번호는 안 된다")
    void resetWithIdentity() throws Exception {
        flow.phone(PERSON, SignUpFlow.PASSWORD);

        MvcResult granted = flow.verifyIdentityForReset(PERSON);
        assertThat(granted.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(granted).get("accountPhone").asText()).isEqualTo("010-****-5678");

        assertThat(reset(read(granted).get("resetToken").asText(), "Changed-pass-99!")
                .getResponse().getStatus()).isEqualTo(204);

        assertThat(login("01012345678", "Changed-pass-99!").getResponse().getStatus()).isEqualTo(200);
        assertThat(login("01012345678", SignUpFlow.PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("재설정하면 다른 기기의 세션이 전부 끊긴다 — 잊은 것과 빼앗긴 것은 구분되지 않는다")
    void resetRevokesOtherSessions() throws Exception {
        flow.phone(PERSON, SignUpFlow.PASSWORD);
        Cookie otherDevice = login("01012345678", SignUpFlow.PASSWORD).getResponse().getCookie("hsg_rt");

        String resetToken = read(flow.verifyIdentityForReset(PERSON)).get("resetToken").asText();
        reset(resetToken, "Changed-pass-99!");

        assertThat(mockMvc.perform(post("/api/v1/auth/refresh").cookie(otherDevice))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("재설정 토큰은 한 번만 쓴다 — 본인확인이 소진된다")
    void resetTokenIsSingleUse() throws Exception {
        flow.phone(PERSON, SignUpFlow.PASSWORD);
        String resetToken = read(flow.verifyIdentityForReset(PERSON)).get("resetToken").asText();

        assertThat(reset(resetToken, "Changed-pass-99!").getResponse().getStatus()).isEqualTo(204);
        MvcResult again = reset(resetToken, "Another-pass-77!");
        assertThat(again.getResponse().getStatus()).isEqualTo(400);
        assertThat(read(again).get("code").asText()).isEqualTo("PASSWORD_RESET_TOKEN_INVALID");
    }

    @Test
    @DisplayName("재설정하면 잠금도 풀린다")
    void resetUnlocks() throws Exception {
        flow.phone(PERSON, SignUpFlow.PASSWORD);
        jdbc.update("UPDATE users SET login_fail_count = 5, locked_at = now()");

        reset(read(flow.verifyIdentityForReset(PERSON)).get("resetToken").asText(), "Changed-pass-99!");

        assertThat(login("01012345678", "Changed-pass-99!").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("본인확인한 신원으로 가입된 계정이 없으면 404 — 회원가입으로 안내한다")
    void unregisteredIdentity() throws Exception {
        MvcResult result = flow.verifyIdentityForReset(PERSON);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(read(result).get("code").asText()).isEqualTo("IDENTITY_NOT_REGISTERED");
    }

    @Test
    @DisplayName("본인인증을 받아 둔 소셜 회원은 여기서 비밀번호를 처음 정할 수 있다")
    void socialOnlyMemberSetsPassword() throws Exception {
        String token = flow.social("google-1", "buyer@test.local", "홍길동");
        flow.verifyIdentityAsMember(token, PERSON);

        String resetToken = read(flow.verifyIdentityForReset(PERSON)).get("resetToken").asText();
        reset(resetToken, "First-pass-11!");

        assertThat(login("01012345678", "First-pass-11!").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("본인인증을 한 번도 받지 않은 소셜 회원은 찾을 수 없다 — 같은 사람인지 알 근거가 없다")
    void unverifiedSocialMemberIsNotFound() throws Exception {
        flow.social("google-1", "buyer@test.local", "홍길동");

        MvcResult result = flow.verifyIdentityForReset(PERSON);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(read(result).get("code").asText()).isEqualTo("IDENTITY_NOT_REGISTERED");
    }
}
