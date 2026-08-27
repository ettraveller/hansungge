package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.global.util.PricePerGram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 보정 4방식의 계산 정확성 (개발계획 §16.3 / 요구사항 7-①).
 *
 * <p>단위 테스트인 이유: 시세 계산은 <b>틀리면 가장 비싼 코드</b>다. DB·트랜잭션·시각을
 * 끼우면 실패했을 때 "계산이 틀린 건지 환경이 틀린 건지"를 먼저 가려야 한다. 순수 함수로
 * 두고 값만으로 검증하면 실패는 곧 계산 오류다.
 *
 * <p>모든 비교는 {@code isEqualByComparingTo} 다 — {@code BigDecimal.equals} 는 scale 까지
 * 보므로 {@code 210000} 과 {@code 210000.00} 을 다르다고 판정한다.
 */
class MarketPriceCalculatorTest {

    private MarketPriceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MarketPriceCalculator();
    }

    // =================================================================
    // 4가지 보정 방식
    // =================================================================

    @Nested
    @DisplayName("보정 방식별 계산")
    class Modes {

        @Test
        @DisplayName("AUTO — 최종 = 원본. 보정액은 0")
        void auto() {
            PriceCalculation result = calculator.apply(
                    PricePerGram.of("200000"), policy(PolicyMode.AUTO, null, null, null));

            assertThat(result.result().value()).isEqualByComparingTo("200000.00");
            assertThat(result.raw().value()).isEqualByComparingTo("200000.00");
            assertThat(result.adjustment()).isEqualByComparingTo("0");
            assertThat(result.hasAdjustment()).isFalse();
        }

        @Test
        @DisplayName("FIXED_ADJ — 요구사항 예시: 200,000 + 10,000 = 210,000")
        void fixedAdjustment() {
            PriceCalculation result = calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.FIXED_ADJ, new BigDecimal("10000"), null, null));

            assertThat(result.result().value()).isEqualByComparingTo("210000.00");
            // 원본은 계산 결과 안에서도 그대로 남는다 — 덮어쓰지 않는다는 원칙의 최소 단위
            assertThat(result.raw().value()).isEqualByComparingTo("200000.00");
            assertThat(result.adjustment()).isEqualByComparingTo("10000.00");
            assertThat(result.mode()).isEqualTo(PolicyMode.FIXED_ADJ);
        }

        @Test
        @DisplayName("FIXED_ADJ — 음수 보정(매입 마진): 200,000 - 14,000 = 186,000")
        void fixedAdjustmentNegative() {
            PriceCalculation result = calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.FIXED_ADJ, new BigDecimal("-14000"), null, null));

            assertThat(result.result().value()).isEqualByComparingTo("186000.00");
            assertThat(result.adjustment()).isEqualByComparingTo("-14000.00");
        }

        @Test
        @DisplayName("PERCENT_ADJ — 레거시 크롤러 상수: 판매 +5%, 매입 -7%")
        void percentAdjustment() {
            PricePerGram raw = PricePerGram.of("200000");

            PriceCalculation sell = calculator.apply(
                    raw, policy(PolicyMode.PERCENT_ADJ, null, new BigDecimal("5"), null));
            assertThat(sell.result().value()).isEqualByComparingTo("210000.00");
            assertThat(sell.adjustment()).isEqualByComparingTo("10000.00");

            PriceCalculation buy = calculator.apply(
                    raw, policy(PolicyMode.PERCENT_ADJ, null, new BigDecimal("-7"), null));
            assertThat(buy.result().value()).isEqualByComparingTo("186000.00");
            assertThat(buy.adjustment()).isEqualByComparingTo("-14000.00");
        }

        @Test
        @DisplayName("PERCENT_ADJ — 나누어떨어지지 않는 비율도 원 단위(소수 2자리)로 정확히 떨어진다")
        void percentAdjustmentRounding() {
            // 137,123 × 1.0333 = 141,689.1959 → NUMERIC(15,2) 에 맞춰 반올림하면 141,689.20
            PriceCalculation result = calculator.apply(
                    PricePerGram.of("137123"),
                    policy(PolicyMode.PERCENT_ADJ, null, new BigDecimal("3.33"), null));

            assertThat(result.result().value()).isEqualByComparingTo("141689.20");
            assertThat(result.result().value().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("MANUAL — 지정가가 있으면 원본과 무관하게 지정가가 최종가다")
        void manualWithPrice() {
            PriceCalculation result = calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.MANUAL, null, null, new BigDecimal("220000")));

            assertThat(result.result().value()).isEqualByComparingTo("220000.00");
            assertThat(result.raw().value()).isEqualByComparingTo("200000.00");
            assertThat(result.adjustment()).isEqualByComparingTo("20000.00");
        }

        @Test
        @DisplayName("MANUAL — 지정가가 없으면 원본을 그대로 쓴다 (V2 시드 상태에서도 수동 고시가 동작해야 한다)")
        void manualWithoutPrice() {
            PriceCalculation result = calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.MANUAL, null, null, null));

            assertThat(result.result().value()).isEqualByComparingTo("200000.00");
            assertThat(result.adjustment()).isEqualByComparingTo("0");
        }
    }

    // =================================================================
    // 0원 방어 (원칙 P6)
    // =================================================================

    @Nested
    @DisplayName("0원 방어")
    class ZeroGuard {

        @Test
        @DisplayName("보정 결과가 0 이 되면 계산 자체가 거부된다 — 0원이 발행될 수 없다")
        void fixedAdjustmentToZero() {
            assertThatThrownBy(() -> calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.FIXED_ADJ, new BigDecimal("-200000"), null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0보다 커야");
        }

        @Test
        @DisplayName("보정 결과가 음수가 되어도 마찬가지로 거부된다")
        void fixedAdjustmentToNegative() {
            assertThatThrownBy(() -> calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.FIXED_ADJ, new BigDecimal("-300000"), null, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("-100% 보정도 거부된다")
        void percentAdjustmentToZero() {
            assertThatThrownBy(() -> calculator.apply(
                    PricePerGram.of("200000"),
                    policy(PolicyMode.PERCENT_ADJ, null, new BigDecimal("-100"), null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    /**
     * 정책 엔티티를 리플렉션으로 만든다.
     *
     * <p>엔티티에 테스트 전용 생성자를 열지 않기 위해서다. 정책은 V2 마이그레이션이
     * 시드하고 관리자 API 로만 바뀌는 것이라, 애플리케이션 코드에 "새로 만드는" 경로가
     * 없어야 한다.
     */
    private MarketPricePolicy policy(PolicyMode mode, BigDecimal adjAmount,
                                     BigDecimal adjPercent, BigDecimal manualPrice) {
        try {
            Constructor<MarketPricePolicy> constructor = MarketPricePolicy.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MarketPricePolicy policy = constructor.newInstance();

            set(policy, "metalType", MetalType.GOLD_24K);
            set(policy, "priceSide", PriceSide.SELL);
            policy.change(mode,
                    adjAmount == null ? BigDecimal.ZERO : adjAmount,
                    adjPercent == null ? BigDecimal.ZERO : adjPercent,
                    manualPrice, "test");
            return policy;
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 정책 생성 실패", e);
        }
    }

    private void set(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
