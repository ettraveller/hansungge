package com.hanseongge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 Rate Limit 검증 (Bucket4j).
 *
 * <p>별도 클래스인 이유: Rate Limit 은 <b>필터 인스턴스가 들고 있는 상태</b>다.
 * 다른 로그인 테스트와 같은 컨텍스트에서 돌리면 서로의 시도 횟수를 갉아먹어
 * 어느 쪽 실패인지 알 수 없는 테스트가 된다. 한도를 3회로 낮춰 별도 컨텍스트에서 검증한다.
 *
 * <p>여기서는 계정을 만들지 않는다. Rate Limit 은 <b>인증보다 앞</b>에서 동작해야 하므로,
 * 존재하지 않는 아이디로도 제한이 걸리는 것이 정확한 검증이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-rate-limit-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=3"
})
class AdminLoginRateLimitIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("IP당 한도를 넘으면 429 를 돌려주고 Retry-After 를 알려준다")
    void blocksAfterLimit() throws Exception {
        String ip = "198.51.100.7";

        // 한도(3회)까지는 인증 실패(401)로 정상 처리된다
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginAttempt(ip))
                    .andExpect(status().isUnauthorized());
        }

        // 4번째부터 인증에 도달하지 못하고 차단된다
        mockMvc.perform(loginAttempt(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    @DisplayName("제한은 IP 단위다 — 다른 IP 는 영향을 받지 않는다")
    void limitIsPerIp() throws Exception {
        String blocked = "198.51.100.8";
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(loginAttempt(blocked));
        }
        mockMvc.perform(loginAttempt(blocked))
                .andExpect(status().isTooManyRequests());

        // 다른 IP 는 그대로 인증 단계까지 간다
        mockMvc.perform(loginAttempt("198.51.100.9"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.RequestBuilder loginAttempt(String ip) throws Exception {
        return post("/api/admin/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("loginId", "test_ratelimit", "password", "Whatever!123")));
    }
}
