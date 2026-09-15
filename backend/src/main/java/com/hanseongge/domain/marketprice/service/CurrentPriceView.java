package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;

/**
 * 한 금속×side 의 현재 시세 상태.
 *
 * <p>"가격"이 아니라 "상태"인 이유: 시세는 <b>아직 고시되지 않았을 수 있다.</b> 그때 0 을
 * 돌려주는 대신 {@link Availability#NONE} 으로 알린다. 화면은 이 값을 보고 "시세문의"를
 * 표시하고 주문을 막는다.
 *
 * <p>한 번 고시된 값은 새 고시가 나올 때까지 유효하다. 시간이 지났다는 이유로 내리지
 * 않는다 — 장이 닫힌 주말·야간에는 값이 바뀔 이유가 없다.
 *
 * @param effective 현재 행. 고시된 적 없으면 null
 */
public record CurrentPriceView(
        MetalType metalType,
        PriceSide priceSide,
        Availability availability,
        MarketPriceEffective effective
) {

    public enum Availability {
        /** 고시되어 있다. 판매에 쓴다 */
        AVAILABLE,
        /** 한 번도 고시된 적 없다 */
        NONE
    }

    public static CurrentPriceView available(MarketPriceEffective effective) {
        return new CurrentPriceView(effective.getMetalType(), effective.getPriceSide(),
                Availability.AVAILABLE, effective);
    }

    public static CurrentPriceView none(MetalType metalType, PriceSide priceSide) {
        return new CurrentPriceView(metalType, priceSide, Availability.NONE, null);
    }

    /** 이 시세로 상품을 팔 수 있는가. false 면 "시세문의" + 주문 차단이다. */
    public boolean isSellable() {
        return availability == Availability.AVAILABLE;
    }
}
