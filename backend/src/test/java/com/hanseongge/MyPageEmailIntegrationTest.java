package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
import com.hanseongge.support.StubMailConfig;
import com.hanseongge.support.StubMailSender;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.StubSmsConfig;
import com.hanseongge.support.StubSmsSender;
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

/**
 * 마이페이지 — 이메일 등록·변경.
 *
 * <p>가입 때 이메일을 건너뛴 회원(소셜 가입자 전부, 자체 가입에서 주소를 비워 둔 회원)이
 * 나중에 주소를 넣는 경로다. 2026-10-03 이전에는 이 길이 없어, 마이페이지에
 * "(등록하지 않음)" 이라고만 적혀 있고 바꿀 방법이 화면에도 API 에도 없었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubSmsConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class MyPageEmailIntegrationTest extends EmbeddedPostgresSupport {

    private static final UserTestFixtures.Person ME =
            UserTestFixtures.personWith("CI-ME", "홍길동", "01012345678");
    private static final UserTestFixtures.Person OTHER =
            UserTestFixtures.personWith("CI-OTHER", "김철수", "01099998888");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;
    @Autowired StubIdentityVerificationProvider identityProvider;
    @Autowired StubSmsSender sms;
    @Autowired StubMailSender mail;

    private SignUpFlow flow;

    @BeforeEach
    void setUp() {
        google.reset();
        identityProvider.reset();
        sms.reset();
        mail.reset();
        UserTestFixtures.deleteAll(jdbc);
        flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult sendCode(String token, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/me/email/code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andReturn();
    }

    private MvcResult change(String token, String email, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/me/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "code", code))))
                .andReturn();
    }

    private String myEmail(String token) throws Exception {
        JsonNode me = read(mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token)).andReturn());
        // 이메일이 없으면 응답에 키 자체가 없다 (Jackson 이 null 필드를 뺀다).
        // get() 은 그때 Java null 을 주므로 path() 로 받아 둘 다 본다.
        JsonNode email = me.path("email");
        return email.isMissingNode() || email.isNull() ? null : email.asText();
    }

    @Test
    @DisplayName("이메일 없이 가입한 회원이 인증번호로 주소를 등록한다")
    void registersEmail() throws Exception {
        String token = flow.phone(ME, SignUpFlow.PASSWORD);
        assertThat(myEmail(token)).isNull();

        assertThat(sendCode(token, "buyer@test.local").getResponse().getStatus()).isEqualTo(204);
        MvcResult saved = change(token, "buyer@test.local", mail.lastCode());

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(saved).get("email").asText()).isEqualTo("buyer@test.local");
        assertThat(myEmail(token)).isEqualTo("buyer@test.local");
    }

    @Test
    @DisplayName("대소문자가 달라도 같은 주소다 — 소문자로 저장한다")
    void normalizesCase() throws Exception {
        String token = flow.phone(ME, SignUpFlow.PASSWORD);

        sendCode(token, "Buyer@Test.Local");
        change(token, "Buyer@Test.Local", mail.lastCode());

        assertThat(myEmail(token)).isEqualTo("buyer@test.local");
    }

    @Test
    @DisplayName("인증번호가 틀리면 저장되지 않는다")
    void rejectsWrongCode() throws Exception {
        String token = flow.phone(ME, SignUpFlow.PASSWORD);
        sendCode(token, "buyer@test.local");

        assertThat(change(token, "buyer@test.local", "000000").getResponse().getStatus()).isEqualTo(400);
        assertThat(myEmail(token)).isNull();
    }

    @Test
    @DisplayName("다른 주소로 받은 번호는 쓸 수 없다 — 번호는 주소에 묶인다")
    void codeIsBoundToAddress() throws Exception {
        String token = flow.phone(ME, SignUpFlow.PASSWORD);
        sendCode(token, "first@test.local");
        String code = mail.lastCode();

        assertThat(change(token, "second@test.local", code).getResponse().getStatus()).isEqualTo(400);
        assertThat(myEmail(token)).isNull();
    }

    @Test
    @DisplayName("한 번 쓴 번호는 다시 쓸 수 없다")
    void codeIsSingleUse() throws Exception {
        String token = flow.phone(ME, SignUpFlow.PASSWORD);
        sendCode(token, "buyer@test.local");
        String code = mail.lastCode();
        change(token, "buyer@test.local", code);

        assertThat(change(token, "buyer@test.local", code).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("남이 쓰는 주소는 메일을 보내기 전에 거절한다")
    void rejectsTakenAddressBeforeSending() throws Exception {
        String other = flow.phone(OTHER, SignUpFlow.PASSWORD);
        sendCode(other, "taken@test.local");
        change(other, "taken@test.local", mail.lastCode());
        mail.reset();

        String token = flow.phone(ME, SignUpFlow.PASSWORD);
        assertThat(sendCode(token, "taken@test.local").getResponse().getStatus()).isEqualTo(409);
        // 보내기 전에 막아야 한다. 번호를 다 받고 마지막에 거절당하면 사용자는
        // 무엇이 잘못됐는지 알 수 없다.
        assertThat(mail.sent).isEmpty();
    }

    @Test
    @DisplayName("로그인하지 않으면 쓸 수 없다")
    void requiresLogin() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/me/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "buyer@test.local"))))
                .andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mail.sent).isEmpty();
    }

    @Test
    @DisplayName("가입 중 받은 번호로는 마이페이지 이메일을 바꿀 수 없다 — 용도가 다르다")
    void signUpCodeDoesNotWorkHere() throws Exception {
        String token = flow.phone(ME, SignUpFlow.PASSWORD);

        // 가입 흐름이 쓰는 EMAIL_VERIFY 용도로 발급된 번호
        String agreed = flow.agree();
        identityProvider.willReturn(OTHER);
        JsonNode pending = flow.verifyIdentity(agreed);
        mockMvc.perform(post("/api/v1/auth/email/verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "signUpToken", pending.get("signUpToken").asText(),
                        "email", "buyer@test.local"))));
        String signUpCode = mail.lastCode();

        assertThat(change(token, "buyer@test.local", signUpCode).getResponse().getStatus()).isEqualTo(400);
        assertThat(myEmail(token)).isNull();
    }
}
