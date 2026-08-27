package com.hanseongge;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
import com.hanseongge.domain.pricing.service.ComponentLine;
import com.hanseongge.domain.pricing.service.ComponentType;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.pricing.service.PriceStatus;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.entity.ProductStatus;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.RoundingPolicy;
import com.hanseongge.global.util.VatMode;
import com.hanseongge.global.util.VatPolicyResolver;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.ProductTestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STEP 7 완료 검증 — 상품 가격 계산 엔진 (설계서 §7, 개발계획 §13).
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>요구사항 예시가 <b>DB·시세·정책을 통과해</b> 837,500 으로 나온다</li>
 *   <li><b>시세를 바꾸면 상품 가격이 자동으로 바뀐다</b> (배치 없이 — 시나리오 S2 의 후반부)</li>
 *   <li>시세 없음·노후 → "시세문의". <b>0원이 아니다</b> (S6)</li>
 *   <li>반올림은 <b>마지막에 한 번만</b> — 구성요소 합계가 소계와 정확히 일치한다</li>
 *   <li>부가세 미확정(A-1) 상태에서도 계산이 멈추지 않는다</li>
 * </ul>
 */
@SpringBootTest
class PriceCalculationIntegrationTest extends EmbeddedPostgresSupport {

    private static final String ADMIN = "test_price_admin";
    private static final Long CATEGORY_ID = 1L;   // V3 시드 '골드바'

    @Autowired PriceCalculationService priceCalculationService;
    @Autowired MarketPriceDeclarationService declarationService;
    @Autowired MarketPriceService marketPriceService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductOptionRepository optionRepository;
    @Autowired SystemSettingService systemSettingService;
    @Autowired VatPolicyResolver vatPolicyResolver;
    @Autowired CacheManager cacheManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void reset() {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        jdbcTemplate.execute("ALTER TABLE market_price_fetch_logs DISABLE TRIGGER trg_mpfl_guard");
        try {
            jdbcTemplate.update("DELETE FROM product_options");
            jdbcTemplate.update("DELETE FROM products");
            jdbcTemplate.update("DELETE FROM market_prices_effective");
            jdbcTemplate.update("DELETE FROM market_price_pending");
            jdbcTemplate.update("DELETE FROM market_price_fetch_logs");
            jdbcTemplate.update("DELETE FROM market_price_raw");
            jdbcTemplate.update(
                    "UPDATE market_price_policies SET mode='MANUAL', adj_amount=0, adj_percent=0, "
                            + "manual_price=NULL, updated_by=NULL");
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
            jdbcTemplate.execute("ALTER TABLE market_price_fetch_logs ENABLE TRIGGER trg_mpfl_guard");
        }
        evictAllCaches();
    }

    // =================================================================
    // 요구사항 예시 (end-to-end)
    // =================================================================

    @Nested
    @DisplayName("요구사항 예시")
    class RequirementExample {

        @Test
        @DisplayName("골드바 3.75g — 시세 210,000 × 3.75 + 공임 50,000 = 837,500")
        void goldBar() {
            // 원본 200,000 + 정액 보정 10,000 → 최종 시세 210,000
            setFixedAdjustment("10000");
            declareMarketPrice("200000");

            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "순금 골드바 3.75g", MetalType.GOLD_24K,
                    PriceType.MARKET_PLUS, "3.75", "50000", "1"));

            PriceQuote quote = priceCalculationService.quote(product.getId(), null);

            assertThat(quote.status()).isEqualTo(PriceStatus.AVAILABLE);
            assertThat(quote.appliedMarketPrice().value()).isEqualByComparingTo("210000.00");
            assertThat(quote.subtotal().value()).isEqualByComparingTo("837500.00");
            assertThat(quote.finalUnitPrice().value()).isEqualByComparingTo("837500.00");

            // 관리자만 보는 근거 — 원본과 보정액이 함께 나온다
            assertThat(quote.rawMarketPrice().value()).isEqualByComparingTo("200000.00");
            assertThat(quote.marketAdjustment()).isEqualByComparingTo("10000.00");
            assertThat(quote.effectivePriceId()).isNotNull();

            // 불변식: 구성요소 합계 = 소계
            assertThat(sum(quote.components())).isEqualByComparingTo(quote.subtotal().value());
        }

        @Test
        @DisplayName("수량을 곱해도 단가는 이미 반올림이 끝난 값이다 — 3개 = 2,512,500")
        void quantity() {
            declareMarketPrice("210000");
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_PLUS, "3.75", "50000", "1"));

            PriceQuote quote = priceCalculationService.quote(product.getId(), null);
            assertThat(quote.totalFor(3).value()).isEqualByComparingTo("2512500.00");
        }
    }

    // =================================================================
    // 시세 변경 → 가격 자동 반영
    // =================================================================

    @Nested
    @DisplayName("시세 변경 전파")
    class MarketPricePropagation {

        @Test
        @DisplayName("시세를 바꾸면 상품 가격이 자동으로 바뀐다 — 상품 테이블을 건드리는 배치는 없다")
        void priceFollowsMarket() {
            declareMarketPrice("200000");
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_LINKED, "3.75", "0", "1"));

            assertThat(priceCalculationService.quote(product.getId(), null).finalUnitPrice().value())
                    .isEqualByComparingTo("750000.00");

            // 상품 행의 상태를 DB 에서 그대로 떠 둔다.
            // 인메모리 엔티티와 비교하면 나노초(Java) ↔ 마이크로초(PostgreSQL) 정밀도 차이로
            // 간헐적으로 어긋난다 — 비교는 같은 출처의 값끼리 해야 한다.
            String before = productRowSnapshot(product.getId());

            // 시세만 바꾼다. 상품은 손대지 않는다
            declareMarketPrice("205000");

            assertThat(priceCalculationService.quote(product.getId(), null).finalUnitPrice().value())
                    .isEqualByComparingTo("768750.00");

            // 상품 행은 한 글자도 바뀌지 않았다 — 저장된 가격이 애초에 없기 때문이다
            assertThat(productRowSnapshot(product.getId())).isEqualTo(before);
        }

        @Test
        @DisplayName("고정가 상품은 시세가 없어도 정상 판매된다")
        void fixedPriceIgnoresMarket() {
            Product product = saveProduct(
                    ProductTestFixtures.fixedPriceProduct(CATEGORY_ID, "포장 케이스", "12000"));

            PriceQuote quote = priceCalculationService.quote(product.getId(), null);

            assertThat(quote.status()).isEqualTo(PriceStatus.AVAILABLE);
            assertThat(quote.finalUnitPrice().value()).isEqualByComparingTo("12000.00");
            assertThat(quote.appliedMarketPrice()).isNull();
        }
    }

    // =================================================================
    // 시세문의 (S6)
    // =================================================================

    @Nested
    @DisplayName("시세문의 폴백")
    class InquiryOnly {

        @Test
        @DisplayName("시세가 고시된 적 없으면 INQUIRY_ONLY 이고 금액은 null 이다 — 0 이 아니다")
        void noMarketPrice() {
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_LINKED, "3.75", "50000", "1"));

            PriceQuote quote = priceCalculationService.quote(product.getId(), null);

            assertThat(quote.status()).isEqualTo(PriceStatus.INQUIRY_ONLY);
            assertThat(quote.status().isOrderable()).isFalse();
            assertThat(quote.finalUnitPrice()).isNull();
            assertThat(quote.subtotal()).isNull();
            assertThat(quote.components()).isEmpty();
            assertThat(quote.unavailableReason()).contains("시세문의");
        }

        @Test
        @DisplayName("시세가 노후되면 값이 있어도 INQUIRY_ONLY 다")
        void staleMarketPrice() {
            declareMarketPrice("200000");
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_LINKED, "3.75", "0", "1"));

            assertThat(priceCalculationService.quote(product.getId(), null).status())
                    .isEqualTo(PriceStatus.AVAILABLE);

            try {
                systemSettingService.update(MarketPriceService.SETTING_STALE_MINUTES, "0", "test");
                evictAllCaches();

                PriceQuote quote = priceCalculationService.quote(product.getId(), null);
                assertThat(quote.status()).isEqualTo(PriceStatus.INQUIRY_ONLY);
                assertThat(quote.finalUnitPrice()).isNull();
                assertThat(quote.unavailableReason()).contains("오래");
            } finally {
                systemSettingService.update(MarketPriceService.SETTING_STALE_MINUTES, "240", "test");
                evictAllCaches();
            }
        }
    }

    // =================================================================
    // 판매 상태
    // =================================================================

    @Nested
    @DisplayName("판매 상태")
    class Availability {

        @Test
        @DisplayName("품절 옵션은 SOLD_OUT — 가격은 계산되지만 주문은 막힌다")
        void soldOutOption() {
            declareMarketPrice("200000");
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_PLUS, "3.75", "0", "1"));
            ProductOption option = optionRepository.save(
                    ProductTestFixtures.option(product.getId(), "3.75g", "3.75", "0", 0));

            PriceQuote quote = priceCalculationService.quote(product.getId(), option.getId());

            assertThat(quote.status()).isEqualTo(PriceStatus.SOLD_OUT);
            assertThat(quote.status().isOrderable()).isFalse();
            assertThat(quote.status().hasPrice()).isTrue();
            assertThat(quote.finalUnitPrice().value()).isEqualByComparingTo("750000.00");
        }

        @Test
        @DisplayName("숨김 상품은 공개 조회에서 NOT_FOR_SALE 이지만, 관리자 미리보기는 계산된다")
        void hiddenProduct() {
            declareMarketPrice("200000");
            Product product = ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "미공개 골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_LINKED, "3.75", "0", "1");
            ProductTestFixtures.setStatus(product, ProductStatus.HIDDEN);
            saveProduct(product);

            PriceQuote publicQuote = priceCalculationService.quote(product.getId(), null);
            assertThat(publicQuote.status()).isEqualTo(PriceStatus.NOT_FOR_SALE);
            assertThat(publicQuote.finalUnitPrice()).isNull();

            // 운영자는 공개 전에 가격을 확인할 수 있어야 한다
            PriceQuote preview = priceCalculationService.preview(product.getId(), null);
            assertThat(preview.finalUnitPrice().value()).isEqualByComparingTo("750000.00");
            assertThat(preview.status()).isEqualTo(PriceStatus.NOT_FOR_SALE);
        }

        @Test
        @DisplayName("옵션이 여럿이면 가장 싼 옵션이 대표가가 된다")
        void representativePrice() {
            declareMarketPrice("200000");
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_PLUS, "3.75", "0", "1"));
            optionRepository.save(ProductTestFixtures.option(product.getId(), "7.5g", "7.5", "0", 5));
            optionRepository.save(ProductTestFixtures.option(product.getId(), "3.75g", "3.75", "0", 5));

            assertThat(priceCalculationService.quoteAllOptions(product.getId())).hasSize(2);
            assertThat(priceCalculationService.representativeQuote(product.getId()))
                    .isPresent()
                    .get()
                    .extracting(q -> q.finalUnitPrice().value())
                    .isEqualTo(new BigDecimal("750000.00"));
        }
    }

    // =================================================================
    // 정책 적용 (A-1 부가세 / A-2 반올림)
    // =================================================================

    @Nested
    @DisplayName("정책 적용")
    class Policies {

        @Test
        @DisplayName("A-1 미확정 상태에서도 계산이 멈추지 않는다 — 임시 NONE, 최종 = 소계")
        void vatUndecided() {
            assertThat(vatPolicyResolver.isUsingTemporaryDefault()).isTrue();

            declareMarketPrice("200000");
            Product product = saveProduct(ProductTestFixtures.marketProduct(
                    CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                    PriceType.MARKET_LINKED, "3.75", "0", "1"));

            PriceQuote quote = priceCalculationService.quote(product.getId(), null);

            assertThat(quote.vatMode()).isEqualTo(VatMode.NONE);
            assertThat(quote.vatPolicyTemporary()).isTrue();
            assertThat(quote.vat().value()).isEqualByComparingTo("0.00");
            // 임시 정책이 금액을 바꾸지 않는다는 것이 핵심이다
            assertThat(quote.finalUnitPrice().value()).isEqualByComparingTo(quote.subtotal().value());
        }

        @Test
        @DisplayName("VAT_MODE=EXCLUDED 로 확정하면 10%가 가산된다 — 재배포 없이 설정만 바꾼다")
        void vatExcluded() {
            try {
                systemSettingService.update(VatPolicyResolver.SETTING_KEY, "EXCLUDED", "test");
                evictAllCaches();

                declareMarketPrice("200000");
                Product product = saveProduct(ProductTestFixtures.marketProduct(
                        CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                        PriceType.MARKET_LINKED, "3.75", "0", "1"));

                PriceQuote quote = priceCalculationService.quote(product.getId(), null);

                assertThat(quote.vatMode()).isEqualTo(VatMode.EXCLUDED);
                assertThat(quote.vatPolicyTemporary()).isFalse();
                assertThat(quote.subtotal().value()).isEqualByComparingTo("750000.00");
                assertThat(quote.vat().value()).isEqualByComparingTo("75000.00");
                assertThat(quote.finalUnitPrice().value()).isEqualByComparingTo("825000.00");
            } finally {
                systemSettingService.update(VatPolicyResolver.SETTING_KEY,
                        SystemSettingService.UNDECIDED, "test");
                evictAllCaches();
            }
        }

        @Test
        @DisplayName("반올림은 마지막에 한 번만 — CEIL_100 이 소계가 아니라 최종 합계에 적용된다")
        void roundingAppliedOnce() {
            try {
                systemSettingService.update("PRICE_ROUNDING_MODE", "CEIL_100", "test");
                evictAllCaches();

                // 137,123 × 3.75 = 514,211.25 + 공임 1,234 = 515,445.25 → CEIL_100 → 515,500
                declareMarketPrice("137123");
                Product product = saveProduct(ProductTestFixtures.marketProduct(
                        CATEGORY_ID, "골드바", MetalType.GOLD_24K,
                        PriceType.MARKET_PLUS, "3.75", "1234", "1"));

                PriceQuote quote = priceCalculationService.quote(product.getId(), null);

                assertThat(quote.roundingPolicy()).isEqualTo(RoundingPolicy.CEIL_100);
                assertThat(quote.roundingPolicyTemporary()).isFalse();

                // 구성요소는 반올림되지 않은 채 정확히 남는다
                assertThat(componentAmount(quote, ComponentType.METAL_BASE))
                        .isEqualByComparingTo("514211.25");
                assertThat(componentAmount(quote, ComponentType.LABOR))
                        .isEqualByComparingTo("1234.00");
                assertThat(quote.subtotal().value()).isEqualByComparingTo("515445.25");

                // 최종에만 백원 올림이 적용된다
                assertThat(quote.finalUnitPrice().value()).isEqualByComparingTo("515500.00");
                assertThat(quote.roundingDelta().value()).isEqualByComparingTo("54.75");

                // 불변식 유지
                assertThat(sum(quote.components())).isEqualByComparingTo(quote.subtotal().value());
            } finally {
                systemSettingService.update("PRICE_ROUNDING_MODE",
                        SystemSettingService.UNDECIDED, "test");
                evictAllCaches();
            }
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    private void declareMarketPrice(String pricePerGram) {
        declarationService.declare(MetalType.GOLD_24K, PriceSide.SELL,
                new BigDecimal(pricePerGram), "테스트 고시", ADMIN);
        evictAllCaches();
    }

    private void setFixedAdjustment(String adjAmount) {
        jdbcTemplate.update(
                "UPDATE market_price_policies SET mode='FIXED_ADJ', adj_amount=? "
                        + "WHERE metal_type='GOLD_24K' AND price_side='SELL'",
                new BigDecimal(adjAmount));
    }

    private BigDecimal sum(List<ComponentLine> components) {
        return components.stream()
                .map(ComponentLine::amount)
                .reduce(Money.ZERO, Money::plus)
                .value();
    }

    private BigDecimal componentAmount(PriceQuote quote, ComponentType type) {
        return quote.components().stream()
                .filter(c -> c.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError(type + " 구성요소가 있어야 합니다"))
                .amount().value();
    }

    /** 상품 행 전체를 문자열로 떠 온다. 어떤 컬럼이든 바뀌면 값이 달라진다. */
    private String productRowSnapshot(Long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT products::text FROM products WHERE id = ?", String.class, productId);
    }

    private void evictAllCaches() {
        for (String name : List.of(CacheConfig.SYSTEM_SETTINGS, CacheConfig.MARKET_PRICES,
                CacheConfig.PRODUCT_PRICES)) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
