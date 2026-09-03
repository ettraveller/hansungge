package com.hanseongge.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 금액/중량/시세 값 객체 검증.
 *
 * <p>여기서 고정하는 것은 STEP 7~10 의 가격 엔진이 딛고 설 바닥이다.
 * 부동소수점이 섞이면 여기서 먼저 깨진다.
 */
class MoneyTest {

    @Test
    @DisplayName("골드바 3.75g — 시세 210,000원/g × 3.75g + 가공비 50,000원 = 837,500원")
    void goldBarPrice() {
        PricePerGram marketPrice = PricePerGram.of(210_000);
        Weight weight = Weight.ofGram("3.75");

        Money base = Money.fromGram(marketPrice, weight);
        Money total = base.plus(Money.of(50_000));

        assertThat(base.value()).isEqualByComparingTo("787500.00");
        assertThat(total.value()).isEqualByComparingTo("837500.00");
    }

    @Test
    @DisplayName("1돈은 3.75g 으로 환산된다")
    void donToGram() {
        assertThat(Weight.ofDon(BigDecimal.ONE).value()).isEqualByComparingTo("3.7500");
        assertThat(Weight.ofGram("7.5").toDon()).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("관리자 보정 — 원본 200,000 + 10,000 = 최종 210,000, 원본 객체는 불변")
    void adjustmentDoesNotMutateOriginal() {
        PricePerGram raw = PricePerGram.of(200_000);
        PricePerGram effective = raw.plusAmount(BigDecimal.valueOf(10_000));

        assertThat(effective.value()).isEqualByComparingTo("210000.00");
        assertThat(raw.value()).isEqualByComparingTo("200000.00");
    }

    @Test
    @DisplayName("매입 마진 -7% 정률 보정")
    void percentAdjustment() {
        assertThat(PricePerGram.of(200_000).plusPercent(BigDecimal.valueOf(-7)).value())
                .isEqualByComparingTo("186000.00");
    }

    @Test
    @DisplayName("18K 파생 — 24K 시세 × 0.75")
    void derivedPrice() {
        assertThat(PricePerGram.of(210_000).deriveBy(new BigDecimal("0.7500")).value())
                .isEqualByComparingTo("157500.00");
    }

    @Test
    @DisplayName("변동률을 계산한다 — 200,000 → 210,000 은 +5%")
    void changePercent() {
        assertThat(PricePerGram.of(210_000).changePercentFrom(PricePerGram.of(200_000)))
                .isEqualByComparingTo("5.00000000");
    }

    @Test
    @DisplayName("시세는 0 이하로 만들 수 없다 — 가격 0원 경로 원천 차단 (원칙 P6)")
    void priceCannotBeZeroOrNegative() {
        assertThatThrownBy(() -> PricePerGram.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PricePerGram.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("반올림 정책이 단위별로 적용된다")
    void rounding() {
        Money amount = Money.of("837532.60");

        assertThat(amount.applyRounding(RoundingPolicy.NONE).value()).isEqualByComparingTo("837532.60");
        assertThat(amount.applyRounding(RoundingPolicy.WON_FLOOR).value()).isEqualByComparingTo("837532.00");
        assertThat(amount.applyRounding(RoundingPolicy.FLOOR_100).value()).isEqualByComparingTo("837500.00");
        assertThat(amount.applyRounding(RoundingPolicy.HALF_UP_1000).value()).isEqualByComparingTo("838000.00");
        assertThat(amount.applyRounding(RoundingPolicy.CEIL_1000).value()).isEqualByComparingTo("838000.00");
    }

    @Test
    @DisplayName("미확정('TBD')·오타 설정값은 파싱되지 않는다 — 호출자가 임시 정책을 선택하게 한다")
    void parseRejectsUndecidedValues() {
        assertThat(RoundingPolicy.parse("TBD")).isEmpty();
        assertThat(RoundingPolicy.parse("")).isEmpty();
        assertThat(RoundingPolicy.parse(null)).isEmpty();
        assertThat(RoundingPolicy.parse("floor_100")).contains(RoundingPolicy.FLOOR_100);
    }

    @Test
    @DisplayName("금액 비교는 scale 과 무관하게 값으로 판정한다")
    void equalityIgnoresScale() {
        assertThat(Money.of("1000")).isEqualTo(Money.of("1000.00"));
        assertThat(Money.of("1000").compareTo(Money.of("1000.00"))).isZero();
    }

    @Test
    @DisplayName("주얼리 구성요소 합산 — 금 + 공임 + 부자재 + 스톤")
    void jewelryComponentSum() {
        Money metal = Money.fromGram(PricePerGram.of(157_500), Weight.ofGram("5.2"));
        Money labor = Money.of(62_400);
        Money material = Money.of(35_000);
        Money stone = Money.of(18_000);

        Money subtotal = Money.sum(metal, labor, material, stone);
        assertThat(subtotal.value()).isEqualByComparingTo("934400.00");

        Money withMargin = subtotal.plus(subtotal.percentOf(BigDecimal.valueOf(8)));
        assertThat(withMargin.value()).isEqualByComparingTo("1009152.00");
    }
}
