package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장바구니 (STEP 10, 개발계획 §13.6).
 *
 * <p>확인하려는 것은 "담고 빼는 것이 되는가"가 아니라 <b>가격이 언제 어떻게 정해지는가</b>다.
 * 담을 때 서버가 계산하는가, 볼 때 다시 계산하는가, 시세를 확인할 수 없을 때 담기가
 * 막히는가 — 이것들이 무너지면 화면 금액과 결제 금액이 갈라진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-cart-tests-0123456789abcdef"
})
class CartIntegrationTest extends EmbeddedPostgresSupport {

    /** V3 시드 카테고리 '골드바'. */
    private static final Long CATEGORY_ID = 1L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired UserJwtProvider userJwtProvider;
    @Autowired MarketPriceDeclarationService declarationService;
    @Autowired CacheManager cacheManager;

    private String token;
    private String otherToken;
    private long productId;
    private long optionId;

    /**
     * 테스트가 끝난 뒤에도 정리한다.
     *
     * <p>임베디드 PostgreSQL 을 테스트 클래스끼리 공유하므로, 장바구니 줄을 남겨두면
     * 시세 테이블을 비우는 다른 클래스가 {@code cart_items} 외래키에 걸려 통째로 실패한다.
     */
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanUp();
    }

    @BeforeEach
    void setUp() {
        cleanUp();

        User buyer = userRepository.save(UserTestFixtures.socialUser("buyer@test.local", "구매자"));
        User other = userRepository.save(UserTestFixtures.socialUser("other@test.local", "다른사람"));
        token = userJwtProvider.issueAccessToken(buyer.getId());
        otherToken = userJwtProvider.issueAccessToken(other.getId());

        declarePrice("147000");
        productId = insertProduct("순금 골드바 3.75g (1돈)", "MARKET_PLUS", "3.75", "50000");
        optionId = insertOption(productId, "3.75g (1돈)", "3.75", 10);
        evictPriceCache();
    }

    private void cleanUp() {
        jdbc.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        try {
            // cart_items 가 effective 를 참조하므로 반드시 먼저 지운다.
            jdbc.update("DELETE FROM cart_items");
            jdbc.update("DELETE FROM carts");
            jdbc.update("DELETE FROM refresh_tokens");
            jdbc.update("DELETE FROM users WHERE email LIKE '%@test.local'");
            jdbc.update("DELETE FROM product_options");
            jdbc.update("DELETE FROM products");
            jdbc.update("DELETE FROM market_prices_effective");
            jdbc.update("DELETE FROM market_price_raw");
            jdbc.update("UPDATE market_price_policies SET mode='MANUAL', adj_amount=0, "
                    + "adj_percent=0, manual_price=NULL");
        } finally {
            jdbc.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
        }
        evictPriceCache();
    }

    // =================================================================
    // 도우미
    // =================================================================

    private void declarePrice(String pricePerGram) {
        declarationService.declare(MetalType.GOLD_24K, PriceSide.SELL,
                new BigDecimal(pricePerGram), "장바구니 테스트 고시", "tester");
        evictPriceCache();
    }

    /**
     * 현재 시세를 마감해 '시세문의' 상태로 만든다.
     *
     * <p>DELETE 하지 않는다. 운영에서 effective 행은 append-only 라 절대 지워지지 않고,
     * 장바구니가 그 행을 외래키로 참조하고 있어 지울 수도 없다. 마감(effective_to 채우기)만이
     * 실제로 일어나는 경로이며, 가드 트리거도 그것만 허용한다.
     */
    private void closeCurrentPrice() {
        jdbc.update("UPDATE market_prices_effective SET effective_to = now() "
                + "WHERE effective_to IS NULL");
        evictPriceCache();
    }

    private void evictPriceCache() {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCT_PRICES);
        if (cache != null) cache.clear();
    }

    /** 상품은 SQL 로 넣는다 — 관리자 API 로 만들려면 관리자 계정까지 준비해야 한다. */
    private long insertProduct(String name, String priceType, String weightG, String laborCost) {
        Long id = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, metal_type, price_type, weight_g,
                                      labor_cost, status, active)
                VALUES (?, ?, 'GOLD_24K', ?, CAST(? AS NUMERIC), CAST(? AS NUMERIC), 'ON_SALE', true)
                RETURNING id
                """, Long.class, CATEGORY_ID, name, priceType, weightG, laborCost);
        return id == null ? 0L : id;
    }

    private long insertOption(long productId, String name, String weightG, int stock) {
        Long id = jdbc.queryForObject("""
                INSERT INTO product_options (product_id, name, weight_g, stock, is_default, active)
                VALUES (?, ?, CAST(? AS NUMERIC), ?, true, true) RETURNING id
                """, Long.class, productId, name, weightG, stock);
        return id == null ? 0L : id;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 금액 비교용.
     *
     * <p>문자열로 견주지 않는다 — BigDecimal 은 {@code 601300.0} 과 {@code 601300.00} 이
     * 같은 값인데 표기가 달라서, 자릿수가 바뀌는 순간 멀쩡한 코드가 실패한다.
     */
    private BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult addToCart(long product, Long option, int quantity) throws Exception {
        Map<String, Object> body = option == null
                ? Map.of("productId", product, "quantity", quantity)
                : Map.of("productId", product, "optionId", option, "quantity", quantity);
        return mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private JsonNode viewCart() throws Exception {
        return read(mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    // =================================================================
    // 접근 제어
    // =================================================================

    @Nested
    @DisplayName("접근 제어")
    class Access {

        @Test
        @DisplayName("비회원은 장바구니를 쓸 수 없다 — 서버에 익명 데이터를 남기지 않는다")
        void anonymousIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("productId", productId, "quantity", 1))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("남의 장바구니 항목은 건드릴 수 없다")
        void cannotTouchOthersItem() throws Exception {
            addToCart(productId, optionId, 1);
            long itemId = viewCart().get("items").get(0).get("id").asLong();

            mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());

            // 내 장바구니는 그대로다
            assertThat(viewCart().get("itemCount").asInt()).isEqualTo(1);
        }
    }

    // =================================================================
    // 담기
    // =================================================================

    @Nested
    @DisplayName("담기 — 금액은 서버가 정한다")
    class Add {

        @Test
        @DisplayName("담으면 서버가 계산한 단가가 기록되고, 합계는 단가×수량이다")
        void addComputesPriceOnServer() throws Exception {
            // 147,000 × 3.75 = 551,250 + 공임 50,000 = 601,250 → 백원 올림 601,300
            addToCart(productId, optionId, 2);

            JsonNode cart = viewCart();
            JsonNode line = cart.get("items").get(0);

            assertThat(money(line, "unitPrice")).isEqualByComparingTo("601300");
            assertThat(line.get("quantity").asInt()).isEqualTo(2);
            assertThat(money(line, "lineAmount")).isEqualByComparingTo("1202600");
            assertThat(money(cart, "subtotal")).isEqualByComparingTo("1202600");
            assertThat(cart.get("orderable").asBoolean()).isTrue();

            // 담은 시점 단가가 DB 에 남는다
            BigDecimal stored = jdbc.queryForObject(
                    "SELECT price_at_add FROM cart_items", BigDecimal.class);
            assertThat(stored).isEqualByComparingTo("601300.00");
        }

        @Test
        @DisplayName("담기 요청에 금액을 넣어도 무시된다 — 프론트가 보낸 가격은 어디서도 읽지 않는다")
        void clientSuppliedPriceIsIgnored() throws Exception {
            mockMvc.perform(post("/api/v1/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId,
                                    "optionId", optionId,
                                    "quantity", 1,
                                    "price", 1000,
                                    "unitPrice", 1000))))
                    .andExpect(status().isOk());

            assertThat(jdbc.queryForObject("SELECT price_at_add FROM cart_items", BigDecimal.class))
                    .isEqualByComparingTo("601300.00");
        }

        @Test
        @DisplayName("같은 상품·옵션을 다시 담으면 줄이 늘지 않고 수량만 더해진다")
        void addingSameLineMergesQuantity() throws Exception {
            addToCart(productId, optionId, 1);
            addToCart(productId, optionId, 2);

            JsonNode cart = viewCart();
            assertThat(cart.get("itemCount").asInt()).isEqualTo(1);
            assertThat(cart.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
            assertThat(cart.get("totalQuantity").asInt()).isEqualTo(3);
        }

        @Test
        @DisplayName("옵션을 지정하지 않으면 기본 옵션에 담긴다 (D-7)")
        void defaultOptionIsUsedWhenOmitted() throws Exception {
            addToCart(productId, null, 1);

            assertThat(viewCart().get("items").get(0).get("optionId").asLong()).isEqualTo(optionId);
        }

        @Test
        @DisplayName("다른 상품의 옵션 ID 를 넣으면 400 — 남의 옵션 재고를 소모시킬 수 없다")
        void foreignOptionIsRejected() throws Exception {
            long another = insertProduct("다른 골드바", "MARKET_PLUS", "7.5", "60000");
            long foreignOption = insertOption(another, "7.5g", "7.5", 5);

            mockMvc.perform(post("/api/v1/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId,
                                    "optionId", foreignOption,
                                    "quantity", 1))))
                    .andExpect(status().isBadRequest());
        }
    }

    // =================================================================
    // 담기 차단 — 이 시스템의 핵심 안전장치
    // =================================================================

    @Nested
    @DisplayName("담을 수 없는 경우는 담기 시점에 막는다")
    class Blocking {

        @Test
        @DisplayName("시세를 확인할 수 없으면 담기지 않는다 — 0원짜리 줄을 만들지 않는다 (S6)")
        void cannotAddWhenPriceUnavailable() throws Exception {
            closeCurrentPrice();

            mockMvc.perform(post("/api/v1/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("productId", productId, "quantity", 1))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRICE_UNAVAILABLE"));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class))
                    .isZero();
        }

        @Test
        @DisplayName("재고보다 많이 담을 수 없다")
        void cannotExceedStock() throws Exception {
            mockMvc.perform(post("/api/v1/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId, "optionId", optionId, "quantity", 11))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        }

        @Test
        @DisplayName("이미 담긴 수량까지 합해 재고를 넘으면 막힌다")
        void cumulativeQuantityChecksStock() throws Exception {
            addToCart(productId, optionId, 8);

            mockMvc.perform(post("/api/v1/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId, "optionId", optionId, "quantity", 5))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

            assertThat(viewCart().get("items").get(0).get("quantity").asInt()).isEqualTo(8);
        }

        @Test
        @DisplayName("판매중이 아닌 상품은 담기지 않는다")
        void cannotAddHiddenProduct() throws Exception {
            jdbc.update("UPDATE products SET status = 'HIDDEN' WHERE id = ?", productId);

            mockMvc.perform(post("/api/v1/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("productId", productId, "quantity", 1))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ON_SALE"));
        }
    }

    // =================================================================
    // 조회 시 재계산
    // =================================================================

    @Nested
    @DisplayName("조회할 때마다 다시 계산한다 (§13.6)")
    class Recalculation {

        @Test
        @DisplayName("담은 뒤 시세가 오르면 현재가와 차액이 함께 나온다")
        void priceChangeIsReported() throws Exception {
            addToCart(productId, optionId, 1);

            // 147,000 → 151,000 (× 3.75 = 566,250 + 50,000 = 616,250 → 616,300)
            declarePrice("151000");

            JsonNode line = viewCart().get("items").get(0);
            assertThat(line.get("priceChanged").asBoolean()).isTrue();
            assertThat(money(line, "unitPrice")).isEqualByComparingTo("616300");
            assertThat(money(line, "priceAtAdd")).isEqualByComparingTo("601300");
            // 차액은 서버가 낸다 — 화면이 빼면 표시 금액과 서버 금액이 갈라진다
            assertThat(money(line, "priceDelta")).isEqualByComparingTo("15000");
        }

        @Test
        @DisplayName("시세가 사라지면 그 줄은 주문 불가가 되고 합계에서 빠진다")
        void unavailableLineIsExcludedFromSubtotal() throws Exception {
            addToCart(productId, optionId, 1);

            closeCurrentPrice();

            JsonNode cart = viewCart();
            JsonNode line = cart.get("items").get(0);

            assertThat(line.get("orderable").asBoolean()).isFalse();
            assertThat(line.get("priceText").asText()).isEqualTo("시세문의");
            assertThat(line.has("unitPrice")).as("금액은 0 이 아니라 없어야 한다").isFalse();
            assertThat(money(cart, "subtotal")).isEqualByComparingTo("0");
            assertThat(cart.get("unavailableCount").asInt()).isEqualTo(1);
            assertThat(cart.get("orderable").asBoolean()).isFalse();
            assertThat(cart.get("notice").asText()).contains("주문할 수 없는");
        }

        @Test
        @DisplayName("담은 뒤 재고가 줄면 부족 사실과 남은 수량을 알려준다")
        void stockShortageIsReported() throws Exception {
            addToCart(productId, optionId, 5);
            jdbc.update("UPDATE product_options SET stock = 2 WHERE id = ?", optionId);

            JsonNode line = viewCart().get("items").get(0);
            assertThat(line.get("stockShortage").asBoolean()).isTrue();
            assertThat(line.get("availableStock").asInt()).isEqualTo(2);
            assertThat(line.get("orderable").asBoolean()).isFalse();
            assertThat(line.get("unavailableReason").asText()).contains("재고");
        }
    }

    // =================================================================
    // 수정 · 삭제
    // =================================================================

    @Nested
    @DisplayName("수량 변경과 삭제")
    class Modify {

        @Test
        @DisplayName("수량 변경은 절대값이다 — 중복 요청에도 두 번 늘지 않는다")
        void quantityChangeIsAbsolute() throws Exception {
            addToCart(productId, optionId, 1);
            long itemId = viewCart().get("items").get(0).get("id").asLong();

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("quantity", 4))))
                        .andExpect(status().isOk());
            }

            assertThat(viewCart().get("items").get(0).get("quantity").asInt()).isEqualTo(4);
        }

        @Test
        @DisplayName("재고를 넘는 수량으로는 바꿀 수 없다")
        void quantityChangeChecksStock() throws Exception {
            addToCart(productId, optionId, 1);
            long itemId = viewCart().get("items").get(0).get("id").asLong();

            mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("quantity", 50))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("0 이하로는 바꿀 수 없다 — 빼려면 삭제한다")
        void zeroQuantityIsRejected() throws Exception {
            addToCart(productId, optionId, 1);
            long itemId = viewCart().get("items").get(0).get("id").asLong();

            mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("quantity", 0))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("삭제와 비우기")
        void removeAndClear() throws Exception {
            long another = insertProduct("다른 골드바", "MARKET_PLUS", "7.5", "60000");
            long anotherOption = insertOption(another, "7.5g", "7.5", 5);
            evictPriceCache();

            addToCart(productId, optionId, 1);
            addToCart(another, anotherOption, 1);
            assertThat(viewCart().get("itemCount").asInt()).isEqualTo(2);

            long itemId = viewCart().get("items").get(0).get("id").asLong();
            mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(1));

            mockMvc.perform(delete("/api/v1/cart").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(0));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class))
                    .isZero();
        }

        @Test
        @DisplayName("개수 조회는 가격 계산 없이 줄 수만 센다")
        void countEndpoint() throws Exception {
            addToCart(productId, optionId, 3);

            mockMvc.perform(get("/api/v1/cart/count").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(1));
        }
    }
}
