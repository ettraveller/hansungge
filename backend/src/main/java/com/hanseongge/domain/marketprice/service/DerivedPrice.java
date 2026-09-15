package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.PriceSide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 24K → 18K·14K 환산 계산. 순수 함수다.
 *
 * <h2>원 미만을 자르는 방향이 살 때와 팔 때가 반대다</h2>
 * 한 방향으로만 자르면 어느 한쪽에서는 회사가 원 미만을 손해 본다.
 * <ul>
 *   <li><b>살 때(판매)</b> — 올림. 고객이 사는 값을 원 미만 때문에 싸게 팔지 않는다</li>
 *   <li><b>팔 때(매입)</b> — 내림. 회사가 사는 값을 원 미만 때문에 비싸게 사지 않는다</li>
 * </ul>
 * 차이는 1원도 안 되지만, 방향이 정해져 있지 않으면 같은 입력이 확인창과 발행에서
 * 다른 값이 된다.
 */
final class DerivedPrice {

    private DerivedPrice() {
    }

    /** {@code base × ratio} 를 원 단위로 자른다. 0 이하가 나와도 여기서 막지 않는다 — 호출부가 거절한다. */
    static BigDecimal of(BigDecimal base, BigDecimal ratio, PriceSide side) {
        RoundingMode mode = side == PriceSide.SELL ? RoundingMode.CEILING : RoundingMode.FLOOR;
        return base.multiply(ratio).setScale(0, mode);
    }
}
