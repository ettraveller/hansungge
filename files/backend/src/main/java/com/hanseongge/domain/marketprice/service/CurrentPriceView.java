package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;

/**
 * 한 금속×side 의 현재 시세 상태.
 *
 * <p>"가격"이 아니라 "상태"인 이유: 시세는 <b>없을 수 있고 낡을 수 있다.</b> 그 두 경우에
 * 0 이나 옛 가격을 돌려주는 대신 {@link Availability} 로 이유를 함께 알린다. 화면은 이
 * 값을 보고 "시세문의"를 표시하고 주문을 막는다 (개발계획 §12.4).
 *
 * @param effective 현재 행. 없으면 null. <b>노후 상태에서도 값은 담아둔다</b> —
 *                  관리자 화면은 "언제 것이 낡았는지"를 보여줘야 하기 때문이다.
 *                  공개 API 는 {@link #isSellable()} 이 false 면 가격을 내보내지 않는다
 */
public record CurrentPriceView(
        MetalType metalType,
        PriceSide priceSide,
        Availability availability,
        MarketPriceEffective effective
) {

    public enum Availability {
        /** 정상. 판매에 사용 가능 */
        AVAILABLE,
        /** 한 번도 고시된 적 없음 */
        NONE,
        /** PRICE_STALE_MINUTES 초과. 값은 있으나 믿을 수 없다 */
        STALE
    }

    public static CurrentPriceView available(MarketPriceEffective effective) {
        return new CurrentPriceView(effective.getMetalType(), effective.getPriceSide(),
                Availability.AVAILABLE, effective);
    }

    public static CurrentPriceView stale(MarketPriceEffective effective) {
        return new CurrentPriceView(effective.getMetalType(), effective.getPriceSide(),
                Availability.STALE, effective);
    }

    public static CurrentPriceView none(MetalType metalType, PriceSide priceSide) {
        return new CurrentPriceView(metalType, priceSide, Availability.NONE, null);
    }

    /** 이 시세로 상품을 팔아도 되는가. false 면 "시세문의" + 주문 차단이다. */
    public boolean isSellable() {
        return availability == Availability.AVAILABLE;
    }
}
