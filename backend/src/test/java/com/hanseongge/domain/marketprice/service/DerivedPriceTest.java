package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.PriceSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 24K → 18K·14K 환산의 원 미만 처리.
 *
 * <p>단위 테스트인 이유: 반올림 방향은 DB·트랜잭션과 무관한 순수 계산이다. 틀리면 매
 * 고시마다 회사가 원 미만을 손해 보는데, 통합 테스트에서는 그 1원이 다른 원인에 묻힌다.
 *
 * <p>비교는 {@code isEqualByComparingTo} 다 — {@code BigDecimal.equals} 는 scale 까지 본다.
 */
class DerivedPriceTest {

    @Test
    @DisplayName("살 때는 원 미만을 올린다 — 220,801 × 0.735 = 162,288.735 → 162,289")
    void sellRoundsUp() {
        assertThat(DerivedPrice.of(new BigDecimal("220801"), new BigDecimal("0.735"), PriceSide.SELL))
                .isEqualByComparingTo("162289");
    }

    @Test
    @DisplayName("팔 때는 원 미만을 내린다 — 188,001 × 0.735 = 138,180.735 → 138,180")
    void buyRoundsDown() {
        assertThat(DerivedPrice.of(new BigDecimal("188001"), new BigDecimal("0.735"), PriceSide.BUY))
                .isEqualByComparingTo("138180");
    }

    @Test
    @DisplayName("나누어떨어지면 올림·내림이 같은 값이다 — 220,800 × 0.75 = 165,600")
    void exactProductIsUnchanged() {
        BigDecimal base = new BigDecimal("220800");
        BigDecimal ratio = new BigDecimal("0.75");

        assertThat(DerivedPrice.of(base, ratio, PriceSide.SELL)).isEqualByComparingTo("165600");
        assertThat(DerivedPrice.of(base, ratio, PriceSide.BUY)).isEqualByComparingTo("165600");
    }

    @Test
    @DisplayName("24K 에 전(錢) 단위가 있어도 곱한 뒤에 한 번만 자른다 — 220,800.50 × 0.5 = 110,400.25")
    void fractionalBaseIsCutOnce() {
        BigDecimal base = new BigDecimal("220800.50");
        BigDecimal ratio = new BigDecimal("0.5");

        assertThat(DerivedPrice.of(base, ratio, PriceSide.SELL)).isEqualByComparingTo("110401");
        assertThat(DerivedPrice.of(base, ratio, PriceSide.BUY)).isEqualByComparingTo("110400");
    }
}
