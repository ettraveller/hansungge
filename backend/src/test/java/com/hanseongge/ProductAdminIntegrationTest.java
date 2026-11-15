package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.product.entity.ProductStatus;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.support.AdminTestFixtures;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 8 완료 검증 — 상품·카테고리·옵션 관리 (설계서 §5, §8, 개발계획 §9).
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>상품 등록 시 <b>기본 옵션이 자동 생성</b>된다 — 재고가 존재할 곳은 항상 하나다 (D-7)</li>
 *   <li>등록 직후에는 <b>노출되지 않는다</b>({@code HIDDEN}). 확인 후 판매를 시작한다</li>
 *   <li>공개 API 는 파라미터로 <b>숨김 상품을 열람할 수 없다</b></li>
 *   <li>가격 재료가 바뀌면 공개 가격이 <b>다음 조회부터 바뀐다</b></li>
 *   <li>상품이 있는 카테고리는 삭제되지 않고, 카테고리 순환 참조가 막힌다</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-product-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000"
})
class ProductAdminIntegrationTest extends EmbeddedPostgresSupport {

    private static final Long CATEGORY_ID = 1L;   // V3 시드 '골드바'

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MarketPriceDeclarationService declarationService;
    @Autowired CacheManager cacheManager;

    private String managerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        AdminUser manager = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "prodmgr", AdminRole.MANAGER);
        managerToken = login(manager.getLoginId());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        try {
            jdbcTemplate.update("DELETE FROM product_images");
            jdbcTemplate.update("DELETE FROM product_options");
            jdbcTemplate.update("DELETE FROM products");
            jdbcTemplate.update("DELETE FROM categories WHERE name LIKE 'test_%'");
            jdbcTemplate.update("DELETE FROM market_prices_effective");
            jdbcTemplate.update("DELETE FROM market_price_raw");
            jdbcTemplate.update("DELETE FROM audit_logs");
            jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?",
                    AdminTestFixtures.PREFIX + "%");
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
        }
        evictPriceCache();
    }

    // =================================================================
    // 상품 등록
    // =================================================================

    @Nested
    @DisplayName("상품 등록")
    class Create {

        @Test
        @DisplayName("등록하면 HIDDEN 으로 생성되고 재고를 담을 기본 옵션이 자동으로 만들어진다")
        void createsHiddenWithDefaultOption() throws Exception {
            long productId = createGoldBar();

            mockMvc.perform(get("/api/admin/products/" + productId).header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HIDDEN"))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.priceType").value("MARKET_PLUS"))
                    // 판매가 필드는 존재하지 않는다 — 가격은 저장되지 않고 계산된다
                    .andExpect(jsonPath("$.price").doesNotExist())
                    .andExpect(jsonPath("$.finalUnitPrice").doesNotExist());

            // 재고가 존재할 곳이 정확히 하나 만들어졌다 (D-7)
            mockMvc.perform(get("/api/admin/products/" + productId + "/options")
                            .header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("3.75g (1돈)"))
                    .andExpect(jsonPath("$[0].stock").value(10))
                    .andExpect(jsonPath("$[0].inStock").value(true));

            // 자동 생성된 옵션은 is_default 여야 한다 (V8, D-7).
            // 이 표시가 빠지면 ux_opt_default 가 아무것도 지키지 못하고, 상품에 기본
            // 옵션이 하나도 없는 상태가 조용히 만들어진다.
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM product_options WHERE product_id = ? AND is_default",
                    Integer.class, productId))
                    .as("재고를 담을 기본 옵션이 정확히 하나 표시되어야 한다")
                    .isEqualTo(1);

            assertThat(countAudit(AuditActions.PRODUCT_CREATE)).isEqualTo(1);
        }

        /**
         * 주문제작 표시 (V33).
         *
         * <p>이 값이 화면의 꼬리표가 아니라 <b>고지의 근거</b>라서 고정한다. 전자상거래법
         * §17②① 단서는 주문제작의 청약철회 제한을 "미리 알린 경우"에만 인정한다 —
         * 값이 잘못 저장되면 제한이 무효가 되어 제작이 끝난 주문도 환불해야 한다.
         *
         * <p>특히 <b>보내지 않았을 때 false</b> 여야 한다. true 로 새면 재고가 있는
         * 상품까지 철회가 제한된 것으로 고지되어, 고객의 권리를 근거 없이 깎는다.
         */
        @Test
        @DisplayName("주문제작 표시는 보낸 대로 저장되고, 보내지 않으면 재고 보유(false)다")
        void madeToOrderDefaultsToFalse() throws Exception {
            long stocked = createGoldBar();   // madeToOrder 를 보내지 않는다
            mockMvc.perform(get("/api/admin/products/" + stocked).header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.madeToOrder").value(false));

            MvcResult created = mockMvc.perform(post("/api/admin/products")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "test_주문제작 반지",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 150000,
                                    "madeToOrder", true,
                                    "initialStock", 0))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.madeToOrder").value(true))
                    .andReturn();

            long custom = objectMapper.readTree(created.getResponse().getContentAsString())
                    .get("id").asLong();

            // 공개 상세에도 나와야 한다. 고지는 고객이 보는 화면에서 일어난다 —
            // 관리자 응답에만 있으면 저장은 됐는데 고지는 안 된 상태가 된다.
            declareMarketPrice("150000");
            startSelling(custom);
            mockMvc.perform(get("/api/v1/products/" + custom))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.madeToOrder").value(true));
        }

        @Test
        @DisplayName("주문제작 표시는 수정으로 되돌릴 수 있다 — 재고를 들이면 기간이 달라진다")
        void madeToOrderIsEditable() throws Exception {
            long id = createGoldBar();

            mockMvc.perform(put("/api/admin/products/" + id)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "순금 골드바 3.75g (1돈)",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 50000,
                                    "madeToOrder", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.madeToOrder").value(true));

            // 다시 재고 보유로. 수정 요청에서 빠지면 false 로 읽히는 쪽이 안전하다.
            mockMvc.perform(put("/api/admin/products/" + id)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "순금 골드바 3.75g (1돈)",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 50000))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.madeToOrder").value(false));
        }

        /**
         * 배송기간·택배사는 상품마다 정한다 (V41).
         *
         * <p>보내지 않으면 null 이다. 빈 문자열이 아니다 — 화면은 null 을 보고
         * 기본 문구(「상품별 상이 · 상세페이지 참조」)를 쓴다. 빈 문자열이 저장되면
         * 상세에 <b>빈 칸</b>이 찍혀, 고지가 없는 것인지 비워 둔 것인지 알 수 없다.
         */
        @Test
        @DisplayName("배송기간·택배사는 보낸 대로 저장되고, 공백만 보내면 null 이 된다")
        void deliveryNoticeIsPerProduct() throws Exception {
            MvcResult created = mockMvc.perform(post("/api/admin/products")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "test_배송기간 있는 반지",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 150000,
                                    "deliveryPeriod", "  결제일부터 최대 4주  ",
                                    "courier", "CJ대한통운",
                                    "initialStock", 0))))
                    .andExpect(status().isCreated())
                    // 앞뒤 공백은 서버가 떼어 낸다. 그대로 두면 화면에서 줄이 밀린다.
                    .andExpect(jsonPath("$.deliveryPeriod").value("결제일부터 최대 4주"))
                    .andExpect(jsonPath("$.courier").value("CJ대한통운"))
                    .andReturn();

            long id = objectMapper.readTree(created.getResponse().getContentAsString())
                    .get("id").asLong();

            // 고객이 보는 화면에 나와야 한다. 관리자 응답에만 있으면 저장은 됐는데
            // 고지는 안 된 상태가 된다.
            declareMarketPrice("150000");
            startSelling(id);
            mockMvc.perform(get("/api/v1/products/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deliveryPeriod").value("결제일부터 최대 4주"))
                    .andExpect(jsonPath("$.courier").value("CJ대한통운"));

            // 공백만 보내면 "안 정했다"로 접힌다 — 빈 칸이 아니라 null 이다.
            mockMvc.perform(put("/api/admin/products/" + id)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "test_배송기간 있는 반지",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 150000,
                                    "deliveryPeriod", "   ",
                                    "courier", ""))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deliveryPeriod").doesNotExist())
                    .andExpect(jsonPath("$.courier").doesNotExist());
        }

        @Test
        @DisplayName("배송기간을 보내지 않은 상품은 null 이다 — 화면이 기본 문구를 쓴다")
        void deliveryNoticeIsNullWhenOmitted() throws Exception {
            long id = createGoldBar();   // deliveryPeriod·courier 를 보내지 않는다
            mockMvc.perform(get("/api/admin/products/" + id).header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deliveryPeriod").doesNotExist())
                    .andExpect(jsonPath("$.courier").doesNotExist());
        }

        @Test
        @DisplayName("시세 연동 상품에 중량이 없으면 400 — DB 제약이 아니라 도메인 오류로 막는다")
        void marketProductRequiresWeight() throws Exception {
            mockMvc.perform(post("/api/admin/products")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "중량 없는 골드바",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_LINKED",
                                    "laborCost", 0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("고정가 상품에 판매가가 없으면 400")
        void fixedProductRequiresPrice() throws Exception {
            mockMvc.perform(post("/api/admin/products")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "가격 없는 케이스",
                                    "priceType", "FIXED"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("없는 카테고리로 등록하면 404")
        void unknownCategory() throws Exception {
            mockMvc.perform(post("/api/admin/products")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", 999999,
                                    "name", "골드바",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_LINKED",
                                    "weightG", 3.75))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        }
    }

    // =================================================================
    // 판매 상태
    // =================================================================

    @Nested
    @DisplayName("판매 상태")
    class Status {

        @Test
        @DisplayName("활성 옵션이 없으면 판매를 시작할 수 없다 — 재고가 존재할 곳이 없기 때문이다")
        void cannotSellWithoutOption() throws Exception {
            long productId = createGoldBar();
            jdbcTemplate.update("UPDATE product_options SET active = false WHERE product_id = ?", productId);

            mockMvc.perform(patch("/api/admin/products/" + productId + "/status")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", "ON_SALE", "reason", "판매 시작"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_LAST_OPTION"));
        }

        @Test
        @DisplayName("마지막 활성 옵션은 비활성화할 수 없다")
        void cannotDisableLastOption() throws Exception {
            long productId = createGoldBar();
            long optionId = firstOptionId(productId);

            mockMvc.perform(put("/api/admin/products/" + productId + "/options/" + optionId)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "3.75g", "sort", 0, "active", false))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_LAST_OPTION"));
        }

        @Test
        @DisplayName("사유 없이 상태를 바꿀 수 없다")
        void statusRequiresReason() throws Exception {
            long productId = createGoldBar();

            mockMvc.perform(patch("/api/admin/products/" + productId + "/status")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", "ON_SALE"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.reason").exists());
        }

        @Test
        @DisplayName("상태 변경은 before/after 가 감사로그에 남는다")
        void statusChangeIsAudited() throws Exception {
            long productId = createGoldBar();
            startSelling(productId);

            Map<String, Object> log = latestAudit(AuditActions.PRODUCT_STATUS_CHANGE);
            assertThat(String.valueOf(log.get("before_value"))).contains("HIDDEN");
            assertThat(String.valueOf(log.get("after_value")))
                    .contains("ON_SALE")
                    .contains("재고 확인 완료");
        }
    }

    // =================================================================
    // 재고
    // =================================================================

    @Nested
    @DisplayName("재고")
    class Stock {

        @Test
        @DisplayName("재고 조정은 절대 수량이라 같은 요청이 두 번 와도 결과가 같다")
        void adjustIsIdempotent() throws Exception {
            long productId = createGoldBar();
            long optionId = firstOptionId(productId);

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(patch("/api/admin/products/" + productId
                                + "/options/" + optionId + "/stock")
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("stock", 7, "reason", "실사 반영"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.stock").value(7));
            }

            assertThat(countAudit(AuditActions.PRODUCT_STOCK_ADJUST)).isEqualTo(2);
        }

        @Test
        @DisplayName("음수 재고는 400")
        void negativeStockRejected() throws Exception {
            long productId = createGoldBar();
            long optionId = firstOptionId(productId);

            mockMvc.perform(patch("/api/admin/products/" + productId
                            + "/options/" + optionId + "/stock")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("stock", -1, "reason", "실수"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("다른 상품의 옵션 ID 를 끼워 넣으면 400 — 남의 재고를 건드릴 수 없다")
        void optionMustBelongToProduct() throws Exception {
            long productA = createGoldBar();
            long productB = createGoldBar("두 번째 골드바");
            long optionB = firstOptionId(productB);

            mockMvc.perform(patch("/api/admin/products/" + productA
                            + "/options/" + optionB + "/stock")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("stock", 99, "reason", "교차 접근"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // =================================================================
    // 카테고리
    // =================================================================

    @Nested
    @DisplayName("카테고리")
    class Categories {

        @Test
        @DisplayName("상품이 있는 카테고리는 삭제되지 않는다 — 상품이 없는 분류를 가리키게 된다")
        void cannotDeleteCategoryInUse() throws Exception {
            createGoldBar();

            mockMvc.perform(delete("/api/admin/categories/" + CATEGORY_ID)
                            .header("Authorization", bearer()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        }

        @Test
        @DisplayName("자기 자신을 부모로 지정하면 409 — 순환 참조는 순회 코드를 무한 루프에 빠뜨린다")
        void selfParentRejected() throws Exception {
            long id = createCategory("test_뿌리", null);

            mockMvc.perform(put("/api/admin/categories/" + id)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "test_뿌리", "sort", 0, "parentId", id))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
        }

        @Test
        @DisplayName("하위 카테고리를 부모로 지정해도 409")
        void descendantParentRejected() throws Exception {
            long parent = createCategory("test_부모", null);
            long child = createCategory("test_자식", parent);

            mockMvc.perform(put("/api/admin/categories/" + parent)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "test_부모", "sort", 0, "parentId", child))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
        }

        @Test
        @DisplayName("빈 카테고리는 삭제된다")
        void deletesEmptyCategory() throws Exception {
            long id = createCategory("test_빈카테고리", null);

            mockMvc.perform(delete("/api/admin/categories/" + id).header("Authorization", bearer()))
                    .andExpect(status().isNoContent());

            assertThat(countAudit(AuditActions.CATEGORY_DELETE)).isEqualTo(1);
        }
    }

    // =================================================================
    // 공개 API
    // =================================================================

    @Nested
    @DisplayName("공개 조회")
    class PublicAccess {

        @Test
        @DisplayName("숨김 상품은 목록에 없고, 상세를 직접 요청해도 404 다 — 403 이면 존재가 새어 나간다")
        void hiddenProductIsInvisible() throws Exception {
            long productId = createGoldBar();   // HIDDEN 상태

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));

            mockMvc.perform(get("/api/v1/products/" + productId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("검색 파라미터로 숨김 상품을 열람할 수 없다 — 노출 조건은 서버가 강제한다")
        void cannotBypassVisibilityViaParams() throws Exception {
            createGoldBar();

            mockMvc.perform(get("/api/v1/products")
                            .param("status", "HIDDEN")
                            .param("active", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("판매를 시작하면 목록·상세에 계산된 가격과 함께 나타난다")
        void visibleAfterStartSelling() throws Exception {
            declareMarketPrice("200000");
            long productId = createGoldBar();
            startSelling(productId);

            // 210,000 아님 — 정책이 MANUAL/지정가없음이라 원본 200,000 이 그대로 최종 시세다
            // 200,000 × 3.75 + 50,000 = 800,000
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].price").value(800000.00))
                    .andExpect(jsonPath("$.content[0].priceText").value("800000.00"))
                    .andExpect(jsonPath("$.content[0].orderable").value(true))
                    // 원가 재료(공임)는 목록에 담기지 않는다
                    .andExpect(jsonPath("$.content[0].laborCost").doesNotExist());

            mockMvc.perform(get("/api/v1/products/" + productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.options.length()").value(1))
                    // 재고 수량은 비공개, 구매 가능 여부만 노출
                    .andExpect(jsonPath("$.options[0].stock").doesNotExist())
                    .andExpect(jsonPath("$.options[0].inStock").value(true))
                    .andExpect(jsonPath("$.prices[0].price").value(800000.00))
                    /*
                     * 가격 내역과 적용 시세는 공개 응답에 없다 (2026-10-30).
                     *
                     * 내역이 공임을 그대로 드러낸다 — 여기서는 800,000 중 50,000 이
                     * 공임이라는 사실이 한 줄로 나왔다. 공임은 상품마다 다르게 매기는
                     * 값이라 공개하면 상품끼리 비교당하고 경쟁사가 마진을 읽는다.
                     *
                     * 화면에서 지우는 것만으로는 부족해서 응답에서 뺐다. 이 단언이
                     * 그 구멍을 다시 열지 못하게 막는다 — 누군가 "상세에 내역을
                     * 보여주자"며 DTO 에 필드를 되살리면 여기서 깨진다.
                     */
                    .andExpect(jsonPath("$.prices[0].components").doesNotExist())
                    .andExpect(jsonPath("$.prices[0].marketPricePerGram").doesNotExist())
                    // 기준 시각은 남긴다. 시세 상품에서 "언제 기준인가"는 고객이 알아야
                    // 하는 정보이고, 그 자체로는 마진을 드러내지 않는다.
                    .andExpect(jsonPath("$.prices[0].priceQuotedAt").exists());
        }

        @Test
        @DisplayName("시세가 없으면 목록에 '시세문의'로 표시되고 금액은 null 이다 — 0 이 아니다")
        void inquiryOnlyWithoutMarketPrice() throws Exception {
            long productId = createGoldBar();
            startSelling(productId);

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].price").doesNotExist())
                    .andExpect(jsonPath("$.content[0].priceText").value("시세문의"))
                    .andExpect(jsonPath("$.content[0].orderable").value(false));
        }

        @Test
        @DisplayName("공임을 바꾸면 공개 가격이 다음 조회부터 바뀐다 — 캐시가 무효화된다")
        void priceFollowsProductEdit() throws Exception {
            declareMarketPrice("200000");
            long productId = createGoldBar();
            startSelling(productId);

            mockMvc.perform(get("/api/v1/products/" + productId + "/price"))
                    .andExpect(jsonPath("$.price").value(800000.00));

            mockMvc.perform(put("/api/admin/products/" + productId)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "순금 골드바 3.75g",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 70000))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/products/" + productId + "/price"))
                    .andExpect(jsonPath("$.price").value(820000.00));
        }

        @Test
        @DisplayName("카테고리 목록은 활성만 나간다")
        void onlyActiveCategories() throws Exception {
            long hidden = createCategory("test_숨긴분류", null);
            mockMvc.perform(put("/api/admin/categories/" + hidden)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "test_숨긴분류", "sort", 0, "active", false))))
                    .andExpect(status().isOk());

            MvcResult result = mockMvc.perform(get("/api/v1/categories"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.toString()).doesNotContain("test_숨긴분류");
        }
    }

    // =================================================================
    // 권한
    // =================================================================

    @Test
    @DisplayName("상위 카테고리로 조회하면 하위 카테고리 상품까지 나온다 — 드롭다운이 헛돌지 않아야 한다")
    void parentCategoryIncludesDescendants() throws Exception {
        // 골드바(1) 아래에 하위 카테고리를 만들고 그쪽에만 상품을 넣는다
        long childId = createCategory("test_기본", CATEGORY_ID);
        long productId = createGoldBarIn(childId, "하위분류 골드바");
        startSelling(productId);

        // 하위 카테고리로 조회하면 당연히 나오고
        mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(childId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 상위 카테고리로 조회해도 나와야 한다
        mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(CATEGORY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("하위분류 골드바"));

        // 무관한 카테고리에는 섞이지 않는다
        mockMvc.perform(get("/api/v1/products").param("categoryId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("OPERATOR 는 조회만 가능하고 등록에서는 403 이다")
    void operatorCannotWrite() throws Exception {
        AdminUser operator = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "prodop", AdminRole.OPERATOR);
        String token = login(operator.getLoginId());

        mockMvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryId", CATEGORY_ID, "name", "골드바",
                                "metalType", "GOLD_24K", "priceType", "MARKET_LINKED",
                                "weightG", 3.75))))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 등록 전 가격 미리보기
    // =================================================================

    @Nested
    @DisplayName("등록 전 상세 미리보기")
    class DraftDetailPreview {

        @Test
        @DisplayName("상품을 만들지 않고 고객 상세와 같은 응답을 돌려준다")
        void doesNotCreateProduct() throws Exception {
            declareMarketPrice("200000");
            int productsBefore = rowCount("products");
            int optionsBefore = rowCount("product_options");
            int imagesBefore = rowCount("product_images");

            mockMvc.perform(post("/api/admin/products/detail-preview")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "순금 골드바 3.75g",
                                    "description", "미리보기 설명",
                                    "galleryKeys", List.of("products/g1.jpg", "products/g2.jpg"),
                                    "detailKeys", List.of("products/d1.jpg"),
                                    "pricing", pricing()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("순금 골드바 3.75g"))
                    .andExpect(jsonPath("$.description").value("미리보기 설명"))
                    // 대표는 갤러리 첫 장이다 — 등록하면 실제로 그렇게 된다.
                    .andExpect(jsonPath("$.thumbnailKey").value("products/g1.jpg"))
                    .andExpect(jsonPath("$.galleryKeys.length()").value(2))
                    .andExpect(jsonPath("$.detailKeys[0]").value("products/d1.jpg"))
                    // 등록 시 자동 생성될 기본 옵션 하나를 가정한다 (D-7).
                    .andExpect(jsonPath("$.options.length()").value(1))
                    // 200,000 × 3.75 + 50,000 = 800,000
                    .andExpect(jsonPath("$.prices[0].price").value(800000))
                    // "판매를 시작하면 이렇게 보인다"를 그린다. HIDDEN 으로 계산하면
                    // 공개 응답이 금액을 가려서, 정작 확인하려던 가격이 안 보인다.
                    .andExpect(jsonPath("$.status").value("ON_SALE"));

            assertThat(rowCount("products")).isEqualTo(productsBefore);
            assertThat(rowCount("product_options")).isEqualTo(optionsBefore);
            assertThat(rowCount("product_images")).isEqualTo(imagesBefore);
        }

        @Test
        @DisplayName("저장한 뒤 고객이 보는 금액과 같다 — 계산식이 하나다")
        void matchesPublishedProduct() throws Exception {
            declareMarketPrice("200000");

            MvcResult draft = mockMvc.perform(post("/api/admin/products/detail-preview")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "순금 골드바 3.75g", "pricing", pricing()))))
                    .andExpect(status().isOk())
                    .andReturn();

            // 같은 재료(3.75g, 공임 50,000)로 실제 등록하고 판매를 시작한다.
            long productId = createGoldBar();
            mockMvc.perform(patch("/api/admin/products/" + productId + "/status")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", "ON_SALE", "active", true,
                                    "reason", "미리보기 대조"))))
                    .andExpect(status().isOk());

            MvcResult live = mockMvc.perform(get("/api/v1/products/" + productId))
                    .andExpect(status().isOk())
                    .andReturn();

            // 미리보기용 계산식을 따로 두면 그쪽이 낡는다. 같은 compute() 를 쓰는지 본다.
            assertThat(firstPriceOf(draft)).isEqualByComparingTo(firstPriceOf(live));
        }

        @Test
        @DisplayName("시세가 없으면 금액 대신 시세문의다 — 0 원을 만들지 않는다")
        void inquiryWhenNoMarketPrice() throws Exception {
            mockMvc.perform(post("/api/admin/products/detail-preview")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "시세 없음", "pricing", pricing()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prices[0].status").value("INQUIRY_ONLY"))
                    .andExpect(jsonPath("$.prices[0].price").doesNotExist());
        }

        @Test
        @DisplayName("상품명이 비어 있어도 열린다 — 가격을 확인하려는 시점이 보통 그 상태다")
        void worksWithoutName() throws Exception {
            declareMarketPrice("200000");

            mockMvc.perform(post("/api/admin/products/detail-preview")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("pricing", pricing()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prices[0].price").value(800000));
        }

        @Test
        @DisplayName("가격 방식에 필요한 값이 없으면 400 이다 — 등록 때와 같은 규칙으로 걸린다")
        void rejectsIncompletePricing() throws Exception {
            // 시세 연동인데 중량이 없다. ProductPricing 생성자가 잡는다.
            mockMvc.perform(post("/api/admin/products/detail-preview")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "중량 없음", "pricing", Map.of(
                                    "priceType", "MARKET_PLUS",
                                    "metalType", "GOLD_24K",
                                    "laborCost", 50000)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("OPERATOR 는 미리보기를 쓸 수 없다")
        void operatorCannotPreviewDraft() throws Exception {
            AdminUser operator = AdminTestFixtures.create(
                    adminUserRepository, passwordEncoder, "prodop", AdminRole.OPERATOR);

            mockMvc.perform(post("/api/admin/products/detail-preview")
                            .header("Authorization", "Bearer " + login(operator.getLoginId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "권한 없음", "pricing", Map.of(
                                    "priceType", "FIXED", "fixedPrice", 250000)))))
                    .andExpect(status().isForbidden());
        }

        /** {@code createGoldBar()} 와 같은 재료. 두 곳이 갈라지면 대조 테스트가 거짓이 된다. */
        private Map<String, Object> pricing() {
            return Map.of(
                    "priceType", "MARKET_PLUS",
                    "metalType", "GOLD_24K",
                    "weightG", 3.75,
                    "laborCost", 50000);
        }

        private BigDecimal firstPriceOf(MvcResult result) throws Exception {
            return objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("prices").get(0).get("price").decimalValue();
        }

        private int rowCount(String table) {
            Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
            return n == null ? 0 : n;
        }
    }

    // =================================================================
    // 이미지 — 대표(GALLERY) · 상세(DETAIL)
    // =================================================================

    @Nested
    @DisplayName("상품 이미지")
    class Images {

        @Test
        @DisplayName("대표이미지를 저장하면 첫 장이 thumbnailKey 가 된다 — 목록·장바구니가 그 값을 읽는다")
        void galleryFirstBecomesThumbnail() throws Exception {
            long productId = createGoldBar();

            putImages(productId, "GALLERY", List.of("products/a.jpg", "products/b.jpg"));

            assertThat(thumbnailOf(productId)).isEqualTo("products/a.jpg");

            // 순서를 바꾸면 대표도 함께 바뀐다. 관리자가 두 곳을 따로 관리하지 않는다.
            putImages(productId, "GALLERY", List.of("products/b.jpg", "products/a.jpg"));
            assertThat(thumbnailOf(productId)).isEqualTo("products/b.jpg");
        }

        @Test
        @DisplayName("대표이미지를 모두 지우면 thumbnailKey 는 null 이다 — 빈 문자열이 아니다")
        void emptyGalleryClearsThumbnail() throws Exception {
            long productId = createGoldBar();
            putImages(productId, "GALLERY", List.of("products/a.jpg"));

            putImages(productId, "GALLERY", List.of());

            // 빈 문자열을 넣으면 화면이 "사진이 있다"로 읽어 깨진 이미지 자리를 그린다.
            assertThat(thumbnailOf(productId)).isNull();
        }

        @Test
        @DisplayName("한 갈래를 바꿔도 다른 갈래는 그대로다 — 대표를 고치다 상세페이지를 잃지 않는다")
        void kindsAreIndependent() throws Exception {
            long productId = createGoldBar();
            putImages(productId, "GALLERY", List.of("products/g1.jpg"));
            putImages(productId, "DETAIL", List.of("products/d1.jpg", "products/d2.jpg"));

            putImages(productId, "GALLERY", List.of("products/g2.jpg", "products/g3.jpg"));

            assertThat(getImages(productId, "GALLERY"))
                    .containsExactly("products/g2.jpg", "products/g3.jpg");
            assertThat(getImages(productId, "DETAIL"))
                    .containsExactly("products/d1.jpg", "products/d2.jpg");
        }

        @Test
        @DisplayName("상세 이미지를 바꿔도 thumbnailKey 는 움직이지 않는다")
        void detailDoesNotTouchThumbnail() throws Exception {
            long productId = createGoldBar();
            putImages(productId, "GALLERY", List.of("products/g1.jpg"));

            putImages(productId, "DETAIL", List.of("products/d1.jpg"));

            assertThat(thumbnailOf(productId)).isEqualTo("products/g1.jpg");
        }

        @Test
        @DisplayName("상품 정보를 저장해도 대표이미지가 사라지지 않는다 — 폼이 thumbnailKey 를 보내지 않는다")
        void updatingProductKeepsThumbnail() throws Exception {
            long productId = createGoldBar();
            putImages(productId, "GALLERY", List.of("products/g1.jpg"));

            // 이름만 고친다. 예전에는 폼에 없는 thumbnailKey 가 null 로 덮어써서
            // 사진을 지운 적이 없는데 목록에서 사라졌다.
            mockMvc.perform(put("/api/admin/products/" + productId)
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "categoryId", CATEGORY_ID,
                                    "name", "이름만 바꾼다",
                                    "metalType", "GOLD_24K",
                                    "priceType", "MARKET_PLUS",
                                    "weightG", 3.75,
                                    "laborCost", 50000))))
                    .andExpect(status().isOk());

            assertThat(thumbnailOf(productId)).isEqualTo("products/g1.jpg");
        }

        @Test
        @DisplayName("공개 상세는 두 갈래를 따로 내려준다")
        void publicDetailSeparatesKinds() throws Exception {
            long productId = createGoldBar();
            putImages(productId, "GALLERY", List.of("products/g1.jpg", "products/g2.jpg"));
            putImages(productId, "DETAIL", List.of("products/d1.jpg"));
            mockMvc.perform(patch("/api/admin/products/" + productId + "/status")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", "ON_SALE", "active", true,
                                    "reason", "이미지 확인 완료"))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/products/" + productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.thumbnailKey").value("products/g1.jpg"))
                    .andExpect(jsonPath("$.galleryKeys.length()").value(2))
                    .andExpect(jsonPath("$.galleryKeys[0]").value("products/g1.jpg"))
                    .andExpect(jsonPath("$.detailKeys.length()").value(1))
                    .andExpect(jsonPath("$.detailKeys[0]").value("products/d1.jpg"));
        }

        private void putImages(long productId, String kind, List<String> keys) throws Exception {
            mockMvc.perform(put("/api/admin/products/" + productId + "/images")
                            .header("Authorization", bearer())
                            .param("kind", kind)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(keys)))
                    .andExpect(status().isOk());
        }

        private List<String> getImages(long productId, String kind) throws Exception {
            MvcResult result = mockMvc.perform(
                            get("/api/admin/products/" + productId + "/images")
                                    .header("Authorization", bearer())
                                    .param("kind", kind))
                    .andExpect(status().isOk())
                    .andReturn();

            List<String> keys = new java.util.ArrayList<>();
            objectMapper.readTree(result.getResponse().getContentAsString())
                    .forEach(node -> keys.add(node.asText()));
            return keys;
        }

        private String thumbnailOf(long productId) {
            return jdbcTemplate.queryForObject(
                    "SELECT thumbnail_key FROM products WHERE id = ?", String.class, productId);
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private long createGoldBar() throws Exception {
        return createGoldBar("순금 골드바 3.75g (1돈)");
    }

    private long createGoldBarIn(long categoryId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryId", categoryId,
                                "name", name,
                                "metalType", "GOLD_24K",
                                "priceType", "MARKET_PLUS",
                                "weightG", 3.75,
                                "laborCost", 50000,
                                "initialStock", 5))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createGoldBar(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryId", CATEGORY_ID,
                                "name", name,
                                "metalType", "GOLD_24K",
                                "priceType", "MARKET_PLUS",
                                "weightG", 3.75,
                                "purity", 0.9999,
                                "laborCost", 50000,
                                "initialStock", 10))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void startSelling(long productId) throws Exception {
        mockMvc.perform(patch("/api/admin/products/" + productId + "/status")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", ProductStatus.ON_SALE.name(),
                                "active", true, "reason", "재고 확인 완료, 판매 시작"))))
                .andExpect(status().isOk());
        evictPriceCache();
    }

    private long createCategory(String name, Long parentId) throws Exception {
        Map<String, Object> body = parentId == null
                ? Map.of("name", name, "sort", 0)
                : Map.of("name", name, "sort", 0, "parentId", parentId);

        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long firstOptionId(long productId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM product_options WHERE product_id = ? ORDER BY id LIMIT 1",
                Long.class, productId);
        assertThat(id).as("기본 옵션이 자동 생성되어야 한다").isNotNull();
        return id;
    }

    private void declareMarketPrice(String pricePerGram) {
        declarationService.declareBoth(MetalType.GOLD_24K, new BigDecimal(pricePerGram),
                new BigDecimal(pricePerGram).multiply(new BigDecimal("0.9"))
                        .setScale(0, java.math.RoundingMode.DOWN),
                "테스트 고시", "test_prodmgr", true);
        evictPriceCache();
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
        return "Bearer " + managerToken;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> latestAudit(String action) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1", action);
        assertThat(rows).as("감사로그 action=%s 가 남아야 한다", action).isNotEmpty();
        return rows.get(0);
    }

    private int countAudit(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
        return count == null ? 0 : count;
    }

    private void evictPriceCache() {
        for (String name : List.of(CacheConfig.PRODUCT_PRICES, CacheConfig.MARKET_PRICES)) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    // =================================================================
    // 진열 순서
    // =================================================================

    @Nested
    @DisplayName("진열 순서")
    class DisplayOrder {

        @Test
        @DisplayName("보낸 차례대로 자리가 매겨지고, 공개 목록이 그 차례로 나온다")
        void savedOrderDrivesPublicList() throws Exception {
            long first = createGoldBarIn(CATEGORY_ID, "test_진열 A");
            long second = createGoldBarIn(CATEGORY_ID, "test_진열 B");
            long third = createGoldBarIn(CATEGORY_ID, "test_진열 C");
            declareMarketPrice("150000");
            startSelling(first);
            startSelling(second);
            startSelling(third);

            // 저장 전에는 최신 등록순이다 (sort 가 전부 0)
            mockMvc.perform(get("/api/v1/products").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(third));

            reorder(List.of(second, third, first));

            mockMvc.perform(get("/api/v1/products").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(second))
                    .andExpect(jsonPath("$.content[1].id").value(third))
                    .andExpect(jsonPath("$.content[2].id").value(first));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT sort FROM products WHERE id = ?", Integer.class, second)).isZero();
            assertThat(countAudit(AuditActions.PRODUCT_REORDER)).isPositive();
        }

        @Test
        @DisplayName("일부만 보내면 거절한다 — 빠진 상품이 자리를 못 받아 맨 앞에 끼어든다")
        void partialListMovesOnlyWhatWasSent() throws Exception {
            long a = createGoldBarIn(CATEGORY_ID, "test_진열 A");
            long b = createGoldBarIn(CATEGORY_ID, "test_진열 B");
            long c = createGoldBarIn(CATEGORY_ID, "test_진열 C");
            declareMarketPrice("150000");
            startSelling(a);
            startSelling(b);
            startSelling(c);

            // 지금 차례는 최신순 C, B, A. 보이지 않는 B 를 빼고 C 와 A 만 뒤집는다.
            reorder(List.of(a, c));

            // C 와 A 가 차지하던 1·3번 자리에 A, C 가 들어가고 B 는 2번에 그대로 있다.
            mockMvc.perform(get("/api/v1/products").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(a))
                    .andExpect(jsonPath("$.content[1].id").value(b))
                    .andExpect(jsonPath("$.content[2].id").value(c));
        }

        @Test
        @DisplayName("없는 상품이 섞여 있으면 거절한다 — 아무것도 바꾸지 않는다")
        void unknownProductIsRejected() throws Exception {
            long first = createGoldBarIn(CATEGORY_ID, "test_진열 A");
            createGoldBarIn(CATEGORY_ID, "test_진열 B");

            mockMvc.perform(put("/api/admin/products/order")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("productIds", List.of(first, 999999)))))
                    .andExpect(status().isNotFound());

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM products WHERE sort <> 0", Integer.class)).isZero();
        }

        @Test
        @DisplayName("같은 상품을 두 번 보내면 거절한다")
        void duplicateIsRejected() throws Exception {
            long first = createGoldBarIn(CATEGORY_ID, "test_진열 A");
            createGoldBarIn(CATEGORY_ID, "test_진열 B");

            mockMvc.perform(put("/api/admin/products/order")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("productIds", List.of(first, first)))))
                    .andExpect(status().isBadRequest());
        }

        private void reorder(List<Long> productIds) throws Exception {
            mockMvc.perform(put("/api/admin/products/order")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("productIds", productIds))))
                    .andExpect(status().isNoContent());
        }
    }
}
