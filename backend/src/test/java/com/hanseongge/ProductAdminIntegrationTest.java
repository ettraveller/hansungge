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
                    .andExpect(jsonPath("$.prices[0].components[0].label").value("순금 24K 시세"));
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
}
