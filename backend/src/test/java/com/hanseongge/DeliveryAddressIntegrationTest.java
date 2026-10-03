package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.order.service.DeliveryAddressService;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 배송지 주소록.
 *
 * <p>주문할 때마다 주소를 처음부터 입력하지 않게 하는 편의 기능이다. 주문은 주소를 자기
 * 칸에 복사해 가므로 여기를 고쳐도 지나간 주문은 바뀌지 않는다 — 그것까지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubSmsConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class DeliveryAddressIntegrationTest extends EmbeddedPostgresSupport {

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

    private String member() throws Exception {
        return flow.phone(UserTestFixtures.person("홍길동"), SignUpFlow.PASSWORD);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> body(String label, String name, boolean setDefault) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("receiverName", name);
        map.put("receiverPhone", "010-1111-2222");
        map.put("zipcode", "06234");
        map.put("address1", "서울 강남구 테헤란로 123 (역삼동)");
        map.put("address2", "4층");
        map.put("setDefault", setDefault);
        return map;
    }

    private MvcResult create(String token, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post("/api/v1/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    private JsonNode list(String token) throws Exception {
        return read(mockMvc.perform(get("/api/v1/me/addresses")
                .header("Authorization", "Bearer " + token)).andReturn());
    }

    @Test
    @DisplayName("첫 배송지는 요청과 무관하게 기본이 된다 — 아니면 주문서가 아무것도 고르지 못한다")
    void firstBecomesDefault() throws Exception {
        String token = member();

        MvcResult created = create(token, body("집", "홍길동", false));

        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(created).get("isDefault").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("기본은 하나뿐이다 — 새로 지정하면 앞의 것이 내려간다")
    void onlyOneDefault() throws Exception {
        String token = member();
        create(token, body("집", "홍길동", false));
        create(token, body("회사", "홍길동", true));

        JsonNode all = list(token);

        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(a -> a.get("isDefault").asBoolean()).hasSize(1);
        // 기본이 맨 위에 온다 — 주문서가 첫 줄을 그대로 고른다.
        assertThat(all.get(0).get("label").asText()).isEqualTo("회사");
    }

    @Test
    @DisplayName("기본 배송지를 지우면 남은 것 중 맨 위가 기본이 된다")
    void deletingDefaultPromotesNext() throws Exception {
        String token = member();
        create(token, body("집", "홍길동", false));
        long second = read(create(token, body("회사", "홍길동", true))).get("id").asLong();

        mockMvc.perform(delete("/api/v1/me/addresses/" + second)
                .header("Authorization", "Bearer " + token)).andReturn();

        JsonNode all = list(token);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).get("isDefault").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("남의 배송지는 보이지도, 지워지지도 않는다 — 403 이 아니라 404 다")
    void cannotTouchSomeoneElses() throws Exception {
        String mine = member();
        String other = member();
        long otherId = read(create(other, body("집", "김철수", true))).get("id").asLong();

        assertThat(mockMvc.perform(delete("/api/v1/me/addresses/" + otherId)
                        .header("Authorization", "Bearer " + mine))
                .andReturn().getResponse().getStatus())
                .isEqualTo(404);
        // 남의 것은 내 목록에 없다
        assertThat(list(mine)).isEmpty();
        assertThat(list(other)).hasSize(1);
    }

    @Test
    @DisplayName("저장 개수에 상한이 있다 — 없으면 주문할 때마다 쌓인다")
    void capped() throws Exception {
        String token = member();
        for (int i = 0; i < DeliveryAddressService.MAX_PER_USER; i++) {
            assertThat(create(token, body("집" + i, "홍길동", false)).getResponse().getStatus()).isEqualTo(200);
        }

        MvcResult overflow = create(token, body("하나 더", "홍길동", false));

        assertThat(overflow.getResponse().getStatus()).isEqualTo(400);
        assertThat(list(token)).hasSize(DeliveryAddressService.MAX_PER_USER);
    }

    @Test
    @DisplayName("로그인하지 않으면 쓸 수 없다")
    void requiresLogin() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/me/addresses"))
                .andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }
}
