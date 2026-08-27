package com.hanseongge.domain.pricing.service;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.entity.ProductPricing;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.support.ProductTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가격 구성요소 정규화 — {@code price_type} 4방식의 산식 정확성 (설계서 §7.1).
 *
 * <p>DB 없이 값만으로 검증한다. 가격 산식은 틀리면 가장 비싼 코드라, 실패했을 때
 * "계산이 틀린 건지 환경이 틀린 건지"를 먼저 가리는 일이 없어야 한다.
 *
 * <p><b>모든 테스트가 확인하는 불변식: 구성요소 합계 = 기대 소계.</b> 이것이 깨지면
 * 주문 명세서의 항목 합이 청구액과 달라진다.
 */
class ProductComponentNormalizerTest {

    private static final PricePerGram MARKET_210K = PricePerGram.of("210000");

    private ProductComponentNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ProductComponentNormalizer();
    }

    // =================================================================
    // 요구사항 예시
    // =================================================================

    @Test
    @DisplayName("요구사항 예시 — 골드바 3.75g: 시세 210,000×3.75 + 공임 50,000 = 837,500")
    void goldBarExample() {
        Product product = ProductTestFixtures.marketProduct(
                1L, "골드바 3.75g", MetalType.GOLD_24K, PriceType.MARKET_PLUS,
                "3.75", "50000", "1");

        List<ComponentLine> lines = normalizer.normalize(product, null, MARKET_210K);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).type()).isEqualTo(ComponentType.METAL_BASE);
        assertThat(lines.get(0).amount().value()).isEqualByComparingTo("787500.00");
        assertThat(lines.get(0).unitPricePerGram().value()).isEqualByComparingTo("210000.00");
        assertThat(lines.get(0).weight().value()).isEqualByComparingTo("3.7500");

        assertThat(lines.get(1).type()).isEqualTo(ComponentType.LABOR);
        assertThat(lines.get(1).amount().value()).isEqualByComparingTo("50000.00");

        assertThat(sum(lines).value()).isEqualByComparingTo("837500.00");
    }

    // =================================================================
    // price_type 별
    // =================================================================

    @Nested
    @DisplayName("price_type 별 산식")
    class PriceTypes {

        @Test
        @DisplayName("MARKET_LINKED — 시세×중량 + 공임")
        void marketLinked() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "실버바", MetalType.GOLD_24K, PriceType.MARKET_LINKED, "10", "30000", "1");

            assertThat(sum(normalizer.normalize(product, null, MARKET_210K)).value())
                    .isEqualByComparingTo("2130000.00");   // 2,100,000 + 30,000
        }

        @Test
        @DisplayName("MARKET_LINKED — 공임이 0이면 그 줄을 만들지 않는다 (0원짜리 항목은 명세서 노이즈다)")
        void marketLinkedWithoutLabor() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "골드바", MetalType.GOLD_24K, PriceType.MARKET_LINKED, "3.75", "0", "1");

            List<ComponentLine> lines = normalizer.normalize(product, null, MARKET_210K);

            assertThat(lines).hasSize(1);
            assertThat(sum(lines).value()).isEqualByComparingTo("787500.00");
        }

        @Test
        @DisplayName("MARKET_PLUS — 옵션 추가공임이 더해진다")
        void marketPlusAddsOptionLabor() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "골드바", MetalType.GOLD_24K, PriceType.MARKET_PLUS, "3.75", "50000", "1");
            ProductOption option = ProductTestFixtures.option(1L, "각인", null, "20000", 5);

            List<ComponentLine> lines = normalizer.normalize(product, option, MARKET_210K);

            assertThat(lines).hasSize(3);
            assertThat(lines.get(2).label()).contains("각인");
            assertThat(sum(lines).value()).isEqualByComparingTo("857500.00");
        }

        @Test
        @DisplayName("MARKET_LINKED — 옵션 추가공임이 있어도 반영하지 않는다 (설계서 §7.1)")
        void marketLinkedIgnoresOptionLabor() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "골드바", MetalType.GOLD_24K, PriceType.MARKET_LINKED, "3.75", "50000", "1");
            ProductOption option = ProductTestFixtures.option(1L, "각인", null, "20000", 5);

            List<ComponentLine> lines = normalizer.normalize(product, option, MARKET_210K);

            assertThat(lines).hasSize(2);
            assertThat(sum(lines).value()).isEqualByComparingTo("837500.00");
        }

        @Test
        @DisplayName("MARKET_MULTIPLIER — (시세×중량)×배수 + 공임. 배수분은 별도 줄로 남는다")
        void marketMultiplier() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "기념품", MetalType.GOLD_24K, PriceType.MARKET_MULTIPLIER,
                    "3.75", "50000", "1.15");

            List<ComponentLine> lines = normalizer.normalize(product, null, MARKET_210K);

            assertThat(lines).hasSize(3);
            // METAL_BASE 줄은 "시세 × 중량" 과 정확히 일치해야 한다 — 배수를 여기에 곱하지 않는다
            assertThat(lines.get(0).amount().value()).isEqualByComparingTo("787500.00");
            assertThat(lines.get(1).type()).isEqualTo(ComponentType.MARGIN);
            assertThat(lines.get(1).amount().value()).isEqualByComparingTo("118125.00");  // ×0.15
            assertThat(lines.get(1).label()).contains("1.15");

            // 787,500 × 1.15 + 50,000 = 955,625
            assertThat(sum(lines).value()).isEqualByComparingTo("955625.00");
        }

        @Test
        @DisplayName("MARKET_MULTIPLIER — 배수가 1이면 가산 줄을 만들지 않는다")
        void multiplierOfOne() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "기념품", MetalType.GOLD_24K, PriceType.MARKET_MULTIPLIER, "3.75", "0", "1");

            assertThat(normalizer.normalize(product, null, MARKET_210K)).hasSize(1);
        }

        @Test
        @DisplayName("FIXED — 시세를 참조하지 않고 fixed_price 를 그대로 쓴다")
        void fixedPrice() {
            Product product = ProductTestFixtures.fixedPriceProduct(1L, "포장 케이스", "12000");

            List<ComponentLine> lines = normalizer.normalize(product, null, null);

            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).method()).isEqualTo(CalcMethod.FIXED_AMOUNT);
            assertThat(sum(lines).value()).isEqualByComparingTo("12000.00");
        }
    }

    // =================================================================
    // 중량 우선순위
    // =================================================================

    @Nested
    @DisplayName("중량 결정")
    class WeightResolution {

        @Test
        @DisplayName("옵션 중량이 상품 중량보다 우선한다 (설계서 §7.1)")
        void optionWeightWins() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "골드바", MetalType.GOLD_24K, PriceType.MARKET_PLUS, "3.75", "0", "1");
            ProductOption option = ProductTestFixtures.option(1L, "7.5g (2돈)", "7.5", "0", 3);

            List<ComponentLine> lines = normalizer.normalize(product, option, MARKET_210K);

            assertThat(lines.get(0).weight().value()).isEqualByComparingTo("7.5000");
            assertThat(sum(lines).value()).isEqualByComparingTo("1575000.00");
        }

        @Test
        @DisplayName("옵션에 중량이 없으면 상품 중량을 쓴다")
        void fallsBackToProductWeight() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "골드바", MetalType.GOLD_24K, PriceType.MARKET_PLUS, "3.75", "0", "1");
            ProductOption option = ProductTestFixtures.option(1L, "각인", null, "0", 3);

            assertThat(normalizer.normalize(product, option, MARKET_210K).get(0).weight().value())
                    .isEqualByComparingTo("3.7500");
        }
    }

    // =================================================================
    // 잘못된 설정
    // =================================================================

    @Nested
    @DisplayName("설정 오류")
    class InvalidConfiguration {

        @Test
        @DisplayName("시세 연동 상품에 중량이 없으면 계산을 거부한다 — 0원을 만들지 않는다")
        void missingWeight() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "골드바", MetalType.GOLD_24K, PriceType.MARKET_LINKED, "3.75", "0", "1");
            setField(product, "weightG", null);

            assertThatThrownBy(() -> normalizer.normalize(product, null, MARKET_210K))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("중량이 없습니다");
        }

        @Test
        @DisplayName("고정가 상품에 fixed_price 가 없으면 계산을 거부한다")
        void missingFixedPrice() {
            Product product = ProductTestFixtures.fixedPriceProduct(1L, "케이스", "12000");
            setField(product, "fixedPrice", null);

            assertThatThrownBy(() -> normalizer.normalize(product, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fixed_price");
        }

        @Test
        @DisplayName("배수가 0 이하면 상품 등록 단계에서 거부된다 — 계산까지 가지 않는다")
        void invalidMultiplierRejectedAtEntry() {
            assertThatThrownBy(() -> new ProductPricing(
                    MetalType.GOLD_24K, PriceType.MARKET_MULTIPLIER, new BigDecimal("3.75"),
                    null, BigDecimal.ZERO, BigDecimal.ZERO, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("배수는 0보다");
        }

        @Test
        @DisplayName("DB 를 직접 고쳐 배수가 0이 된 데이터도 계산기가 한 번 더 막는다")
        void invalidMultiplierGuardedAtCalculation() {
            Product product = ProductTestFixtures.marketProduct(
                    1L, "기념품", MetalType.GOLD_24K, PriceType.MARKET_MULTIPLIER, "3.75", "0", "1.15");
            setField(product, "multiplier", BigDecimal.ZERO);

            assertThatThrownBy(() -> normalizer.normalize(product, null, MARKET_210K))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("multiplier");
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private Money sum(List<ComponentLine> lines) {
        return lines.stream().map(ComponentLine::amount).reduce(Money.ZERO, Money::plus);
    }

    private void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("소수 중량도 전(錢) 단위까지 정확하다 — 5.2g × 157,500 = 819,000")
    void fractionalWeight() {
        Product product = ProductTestFixtures.marketProduct(
                1L, "18K 목걸이", MetalType.GOLD_18K, PriceType.MARKET_LINKED, "5.2", "0", "1");

        List<ComponentLine> lines = normalizer.normalize(product, null, PricePerGram.of("157500"));

        assertThat(sum(lines).value()).isEqualByComparingTo("819000.00");
        assertThat(sum(lines).value().scale()).isEqualTo(2);
        assertThat(lines.get(0).rate()).isNull();
        assertThat(lines.get(0).amount().value()).isEqualByComparingTo(new BigDecimal("819000.00"));
    }
}
