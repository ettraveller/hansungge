package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 인증번호(문자) 로그인.
 *
 * <p>휴대폰번호는 가입 때 본인확인으로 명의가 검증됐다. 로그인에서는 그 번호를 <b>지금 가지고
 * 있는지</b>만 확인한다 — 번호당 60초·시간당 5통, 5분·5회, 성공 즉시 폐기.
 *
 * <p>쿨다운은 기본값(60초) 그대로 둔다. 쿨다운 자체를 검증해야 하므로 끄지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubSmsConfig.class})
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

    private Long memberId;

    @BeforeEach
    void setUp() throws Exception {
        google.reset();
        identityProvider.reset();
        sms.reset();
        UserTestFixtures.deleteAll(jdbc);

        SignUpFlow flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);
        flow.phone(UserTestFixtures.identityWith("DI-1", "홍길동", PHONE), SignUpFlow.PASSWORD);
        memberId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        // 가입 과정에서 쌓인 것이 없게
        jdbc.update("DELETE FROM verification_codes");
    }

    private MvcResult send(String phone) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
                .andReturn();
    }

    private MvcResult login(String phone, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", code))))
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
}
