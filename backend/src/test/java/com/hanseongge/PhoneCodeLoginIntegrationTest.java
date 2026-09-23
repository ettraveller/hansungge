package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.StubMailConfig;
import com.hanseongge.support.StubMailSender;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 인증번호 로그인 — 문자와 이메일.
 *
 * <p>휴대폰번호는 가입 때 본인인증으로 명의가 검증됐다. 로그인에서는 그 번호를 <b>지금 가지고
 * 있는지</b>만 확인한다 — 대상당 60초, 문자는 시간당 5통, 5분·5회, 성공 즉시 폐기.
 *
 * <p>이메일은 <b>가입 때 인증번호로 소유를 확인한 주소</b>만 통한다. 확인하지 않은 주소로 열어 두면
 * 남의 주소를 적어 둔 계정에 그 주소 주인이 들어간다.
 *
 * <p>쿨다운은 기본값(60초) 그대로 둔다. 쿨다운 자체를 검증해야 하므로 끄지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubSmsConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class PhoneCodeLoginIntegrationTest extends EmbeddedPostgresSupport {

    private static final String PHONE = "01012345678";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;
    @Autowired StubIdentityVerificationProvider identityProvider;
    @Autowired StubSmsSender sms;
    @Autowired StubMailSender mail;

    private Long memberId;

    @BeforeEach
    void setUp() throws Exception {
        google.reset();
        identityProvider.reset();
        sms.reset();
        mail.reset();
        UserTestFixtures.deleteAll(jdbc);

        SignUpFlow flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);
        flow.phone(UserTestFixtures.personWith("CI-1", "홍길동", PHONE), SignUpFlow.PASSWORD);
        memberId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        // 가입 과정에서 쌓인 것이 없게
        jdbc.update("DELETE FROM verification_codes");
    }

    private MvcResult send(String phone) throws Exception {
        return send("SMS", phone);
    }

    private MvcResult send(String channel, String target) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/code/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("channel", channel, "target", target))))
                .andReturn();
    }

    private MvcResult login(String phone, String code) throws Exception {
        return login("SMS", phone, code);
    }

    private MvcResult login(String channel, String target, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/code/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("channel", channel, "target", target, "code", code))))
                .andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("문자로 받은 번호로 로그인되고, 비밀번호 로그인과 같은 계정이다")
    void codeLoginReachesTheSameAccount() throws Exception {
        assertThat(send("010-1234-5678").getResponse().getStatus()).isEqualTo(204);
        assertThat(sms.sent).hasSize(1);
        assertThat(sms.sent.get(0).phone()).isEqualTo(PHONE);

        MvcResult result = login(PHONE, sms.lastCode());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(result).get("user").get("id").asLong()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("한 번 쓴 번호는 다시 통하지 않는다")
    void codeIsConsumedOnSuccess() throws Exception {
        send(PHONE);
        String code = sms.lastCode();
        assertThat(login(PHONE, code).getResponse().getStatus()).isEqualTo(200);

        MvcResult again = login(PHONE, code);
        assertThat(again.getResponse().getStatus()).isEqualTo(400);
        assertThat(read(again).get("code").asText()).isEqualTo("VERIFICATION_CODE_INVALID");
    }

    @Test
    @DisplayName("원문은 DB 에 없다 — 해시만 저장된다")
    void codeIsStoredAsHash() throws Exception {
        send(PHONE);
        String stored = jdbc.queryForObject("SELECT code_hash FROM verification_codes", String.class);
        assertThat(stored).isNotEqualTo(sms.lastCode()).hasSize(64);
    }

    @Test
    @DisplayName("가입하지 않은 번호도 같은 응답이다 — 다만 문자는 보내지 않는다")
    void unknownNumberGetsSameResponseButNoSms() throws Exception {
        MvcResult result = send("01099999999");
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(sms.sent).isEmpty();
    }

    @Test
    @DisplayName("60초 안에 다시 요청하면 429 — 회원이든 아니든 똑같이")
    void cooldownAppliesEqually() throws Exception {
        send(PHONE);
        assertThat(send(PHONE).getResponse().getStatus()).isEqualTo(429);

        send("01099999999");
        assertThat(send("01099999999").getResponse().getStatus())
                .as("비회원만 쿨다운이 없으면 두 번째 응답으로 가입 여부가 드러난다")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("한 번호로 한 시간에 5통을 넘길 수 없다 — 문자 폭탄 방지")
    void hourlyLimit() throws Exception {
        // 지난 1시간 안에(쿨다운은 지나서) 이미 5통을 보냈다
        for (int i = 0; i < 5; i++) {
            jdbc.update("""
                    INSERT INTO verification_codes (target, channel, code_hash, purpose, expires_at, created_at)
                    VALUES (?, 'SMS', 'x', 'LOGIN', now(), now() - interval '10 minutes')
                    """, PHONE);
        }
        MvcResult result = send(PHONE);
        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(sms.sent).isEmpty();
    }

    @Test
    @DisplayName("5번 틀리면 그 번호는 더 이상 통하지 않는다")
    void attemptsAreLimited() throws Exception {
        send(PHONE);
        String right = sms.lastCode();
        String wrong = right.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            login(PHONE, wrong);
        }
        MvcResult result = login(PHONE, right);
        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(read(result).get("code").asText()).isEqualTo("VERIFICATION_ATTEMPTS_EXCEEDED");
    }

    @Test
    @DisplayName("비밀번호 실패로 잠긴 계정도 인증번호로는 들어온다 — 휴대폰을 가진 본인이다")
    void codeLoginWorksOnLockedAccount() throws Exception {
        jdbc.update("UPDATE users SET login_fail_count = 5, locked_at = now()");

        send(PHONE);
        assertThat(login(PHONE, sms.lastCode()).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT locked_at IS NULL FROM users", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("문자 설정이 없으면 503 — 로그인 화면도 버튼을 그리지 않는다")
    void unavailableWithoutSms() throws Exception {
        sms.configured = false;
        MvcResult result = send(PHONE);
        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(read(result).get("code").asText()).isEqualTo("SMS_UNAVAILABLE");
    }

    // =================================================================
    // 이메일로 받기 (2026-09-23)
    // =================================================================

    @Test
    @DisplayName("인증한 이메일로도 같은 계정에 로그인된다 — 문자비가 들지 않는다")
    void emailCodeLogin() throws Exception {
        jdbc.update("UPDATE users SET email = 'buyer@test.local', email_verified_at = now()");

        assertThat(send("EMAIL", "Buyer@Test.local").getResponse().getStatus()).isEqualTo(204);
        assertThat(mail.sent).hasSize(1);
        assertThat(sms.sent).as("이메일을 골랐으면 문자는 나가지 않는다").isEmpty();

        MvcResult result = login("EMAIL", "buyer@test.local", mail.lastCode());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(result).get("user").get("id").asLong()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("인증하지 않은 이메일로는 로그인되지 않는다 — 남의 주소를 적어 둔 계정을 지킨다")
    void unverifiedEmailCannotLogIn() throws Exception {
        jdbc.update("UPDATE users SET email = 'buyer@test.local', email_verified_at = NULL");

        assertThat(send("EMAIL", "buyer@test.local").getResponse().getStatus()).isEqualTo(204);
        assertThat(mail.sent).as("회원으로 보지 않으므로 메일도 나가지 않는다").isEmpty();
    }

    @Test
    @DisplayName("문자로 받은 번호를 이메일 쪽에 넣어도 통하지 않는다 — 채널까지 대조한다")
    void channelIsPartOfTheMatch() throws Exception {
        jdbc.update("UPDATE users SET email = 'buyer@test.local', email_verified_at = now()");
        send(PHONE);

        MvcResult result = login("EMAIL", "buyer@test.local", sms.lastCode());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(read(result).get("code").asText()).isEqualTo("VERIFICATION_CODE_INVALID");
    }

    @Test
    @DisplayName("메일 설정이 없으면 503 — 화면도 이메일 선택지를 그리지 않는다")
    void unavailableWithoutMail() throws Exception {
        mail.configured = false;
        MvcResult result = send("EMAIL", "buyer@test.local");
        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(read(result).get("code").asText()).isEqualTo("MAIL_UNAVAILABLE");
    }
}
