package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.support.AdminTestFixtures;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시세 고시의 <b>단위 경계</b> — 관리자는 돈(3.75g)으로 고시하고, 저장은 원/g 이다.
 *
 * <h2>왜 따로 있는가</h2>
 * {@code MarketPriceIntegrationTest} 는 {@code MarketPriceDeclarationService} 를 직접
 * 부른다. 그 층은 계속 원/g 이라 단위가 바뀌어도 아무것도 깨지지 않았다 — 즉
 * <b>환산이 일어나는 자리를 아무도 검증하지 않았다.</b> 여기서 HTTP 로 넣는다.
 *
 * <p>이 경계가 틀어지면 판매가가 3.75배 또는 1/3.75배로 나가고 주문이 그대로 접수된다.
 * 화면에는 아무 오류도 안 뜬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-don-unit-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000"
})
class MarketPriceDonUnitIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        AdminUser admin = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "donadmin", AdminRole.ADMIN);
        token = login(admin.getLoginId());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        try {
            jdbcTemplate.update("DELETE FROM market_prices_effective");
            jdbcTemplate.update("DELETE FROM market_price_raw");
            jdbcTemplate.update("DELETE FROM audit_logs");
            jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?",
                    AdminTestFixtures.PREFIX + "%");
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
        }
    }

    @Test
    @DisplayName("돈 단위로 고시하면 3.75 로 나눈 값이 원/g 으로 저장된다")
    void storesPerGram() throws Exception {
        declare("828000", "705000");

        // 828,000 / 3.75 = 220,800 — 나누어떨어지는 값이라 정확히 맞는다.
        assertThat(storedPerGram("SELL")).isEqualByComparingTo("220800.00");
        assertThat(storedPerGram("BUY")).isEqualByComparingTo("188000.00");
    }

    @Test
    @DisplayName("고시한 돈 값이 관리자 화면에 그대로 돌아온다 — 나누어떨어지지 않아도")
    void roundTripsThroughDisplay() throws Exception {
        // 662,000 / 3.75 = 176,533.333… 자리를 잃는 값이다. 되돌렸을 때 고시한 값과
        // 달라 보이면 관리자가 "내가 잘못 넣었나"를 의심하게 된다.
        declare("662000", "620000");

        JsonNode sell = currentOf("SELL");
        assertThat(sell.get("pricePerGram").decimalValue()).isEqualByComparingTo("176533.33");
        // 176,533.33 × 3.75 = 661,999.9875. perDonFromGram 이 원 단위로 반올림하므로
        // 고시한 값과 정확히 같게 돌아온다 — 소수가 실려 나가면 관리자가 자기 입력을
        // 의심한다.
        assertThat(sell.get("pricePerDon").decimalValue()).isEqualByComparingTo("662000");
    }

    @Test
    @DisplayName("미리보기가 돌려주는 돈 값은 방금 입력한 값과 같다")
    void previewSpeaksDon() throws Exception {
        declare("820000", "700000");

        MvcResult result = mockMvc.perform(post("/api/admin/prices/apply-pair/preview")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "metalType", "GOLD_24K",
                                "sellPricePerDon", "828000",
                                "buyPricePerDon", "705000"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode line = lineOf(objectMapper.readTree(result.getResponse().getContentAsString()));
        // 입력한 값이 "바뀔 값"으로 그대로 보여야 한다. g 로 보여주면 관리자가 방금 친
        // 숫자와 달라서 맞게 들어갔는지 확인할 수 없다.
        assertThat(line.get("newPricePerDon").decimalValue()).isEqualByComparingTo("828000.00");
        assertThat(line.get("currentPricePerDon").decimalValue()).isEqualByComparingTo("820000.00");
        // g 값도 함께 온다 — 상품 가격 계산은 그쪽을 쓴다.
        assertThat(line.get("newPricePerGram").decimalValue()).isEqualByComparingTo("220800.00");
    }

    @Test
    @DisplayName("1g 가격을 돈 칸에 넣으면 거절된다 — 환산 뒤 1/3.75 로 떨어지는 것을 잡는다")
    void rejectsGramPriceInDonField() throws Exception {
        declare("828000", "705000");
        int before = count("market_prices_effective");

        // 관리자가 습관대로 g 가격(220,800)을 돈 칸에 넣었다.
        mockMvc.perform(post("/api/admin/prices/apply-pair")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "metalType", "GOLD_24K",
                                "sellPricePerDon", "220800",
                                "buyPricePerDon", "188000",
                                "changeReason", "단위 실수",
                                // 확인 버튼을 눌러도 통과하지 못해야 한다.
                                "confirmLargeChange", true))))
                .andExpect(status().isBadRequest());

        // 아무것도 바뀌지 않았다 — 행이 늘지도 않았다.
        assertThat(storedPerGram("SELL")).isEqualByComparingTo("220800.00");
        assertThat(count("market_prices_effective")).isEqualTo(before);
    }

    @Test
    @DisplayName("옛 필드명(perGram)으로 보내면 400 이다 — 프론트와 계약이 어긋나면 조용히 통과하지 않는다")
    void rejectsLegacyFieldNames() throws Exception {
        mockMvc.perform(post("/api/admin/prices/apply-pair")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "metalType", "GOLD_24K",
                                "sellPricePerGram", "220800",
                                "buyPricePerGram", "188000",
                                "changeReason", "옛 계약"))))
                .andExpect(status().isBadRequest())
                // @NotNull 이 잡는다 — 값이 null 로 들어오기 때문이다. 이것이 없으면
                // 0 이나 예외 없는 통과로 이어질 수 있다.
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(count("market_prices_effective")).isZero();
    }

    @Test
    @DisplayName("돈 → g → 돈 왕복은 고시한 값을 그대로 돌려준다")
    void conversionRoundTripIsExact() {
        // g 값은 소수점 두 자리까지만 저장되므로(numeric(15,2)) 3.75 로 나누어떨어지지
        // 않는 값은 자리를 잃는다. 그 손실이 돈으로 되돌릴 때 최대 ±0.01875 이고,
        // perDonFromGram 이 원 단위로 반올림하므로 입력과 같은 값이 나온다.
        for (String don : List.of("662000", "662250", "787500", "513751", "137003", "1")) {
            BigDecimal input = new BigDecimal(don);
            BigDecimal perGram = MetalType.perGramFromDon(input);

            assertThat(perGram.scale()).as("저장 자리수").isEqualTo(2);
            assertThat(perGram.multiply(MetalType.GRAMS_PER_DON).subtract(input).abs())
                    .as("%s원/돈 — 반올림 전 오차", don)
                    .isLessThanOrEqualTo(new BigDecimal("0.01875"));
            assertThat(MetalType.perDonFromGram(perGram))
                    .as("%s원/돈 왕복", don)
                    .isEqualByComparingTo(input);
        }

        assertThat(MetalType.perGramFromDon(null)).isNull();
        assertThat(MetalType.perDonFromGram(null)).isNull();
    }

    // =================================================================
    // 도우미
    // =================================================================

    // =================================================================
    // 매입 계산기 — 고객이 "내 금 팔면 얼마인가"를 묻는 길
    // =================================================================

    @Test
    @DisplayName("매입 예상액은 서버가 낸다 — 매입가(원/g) × 중량, 원 단위로 끊는다")
    void buybackQuoteIsComputedByServer() throws Exception {
        // 팔 때 705,000원/돈 → 188,000원/g
        declare("828000", "705000");

        MvcResult result = mockMvc.perform(get("/api/v1/prices/buyback")
                        .param("metal", "GOLD_24K")
                        .param("weightG", "7.5"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("available").asBoolean()).isTrue();
        assertThat(body.get("pricePerGram").decimalValue()).isEqualByComparingTo("188000.00");
        assertThat(body.get("pricePerDon").decimalValue()).isEqualByComparingTo("705000");
        // 7.5g = 2돈 → 188,000 × 7.5 = 1,410,000
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("1410000");
        assertThat(body.get("weightDon").decimalValue()).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("고시된 적 없는 금속은 0원이 아니라 '시세문의'로 돌아온다 — 원칙 P6")
    void buybackReturnsNoPriceInsteadOfZero() throws Exception {
        // 은은 고시한 적이 없다.
        MvcResult result = mockMvc.perform(get("/api/v1/prices/buyback")
                        .param("metal", "SILVER")
                        .param("weightG", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("available").asBoolean()).isFalse();
        // 0 이 아니라 아예 없어야 한다. 0 원은 "안 쳐 준다"로 읽힌다.
        assertThat(body.hasNonNull("amount")).isFalse();
        assertThat(body.hasNonNull("pricePerGram")).isFalse();
    }

    @Test
    @DisplayName("중량이 0 이거나 음수면 거절한다 — 0원 견적을 만들지 않는다")
    void buybackRejectsNonPositiveWeight() throws Exception {
        declare("828000", "705000");

        for (String weight : new String[] {"0", "-1"}) {
            mockMvc.perform(get("/api/v1/prices/buyback")
                            .param("metal", "GOLD_24K")
                            .param("weightG", weight))
                    .andExpect(status().isBadRequest());
        }
    }

    private void declare(String sellPerDon, String buyPerDon) throws Exception {
        mockMvc.perform(post("/api/admin/prices/apply-pair")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "metalType", "GOLD_24K",
                                "sellPricePerDon", sellPerDon,
                                "buyPricePerDon", buyPerDon,
                                "changeReason", "테스트 고시",
                                "confirmLargeChange", true))))
                .andExpect(status().isOk());
    }

    private BigDecimal storedPerGram(String side) {
        return jdbcTemplate.queryForObject(
                "SELECT price_per_gram FROM market_prices_effective "
                        + " WHERE metal_type = 'GOLD_24K' AND price_side = ? AND effective_to IS NULL",
                BigDecimal.class, side);
    }

    /** 24K 의 한쪽 현재 시세. */
    private JsonNode currentOf(String side) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/prices/current")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode row : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if ("GOLD_24K".equals(row.get("metalType").asText())
                    && side.equals(row.get("priceSide").asText())) {
                return row;
            }
        }
        throw new AssertionError("GOLD_24K/" + side + " 가 현재 시세에 없다");
    }

    /** 미리보기 계획에서 24K SELL 줄. */
    private JsonNode lineOf(JsonNode plan) {
        for (JsonNode line : plan.get("lines")) {
            if ("GOLD_24K".equals(line.get("metalType").asText())
                    && "SELL".equals(line.get("priceSide").asText())) {
                return line;
            }
        }
        throw new AssertionError("GOLD_24K/SELL 줄이 미리보기에 없다");
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private String login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId,
                                "password", AdminTestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
