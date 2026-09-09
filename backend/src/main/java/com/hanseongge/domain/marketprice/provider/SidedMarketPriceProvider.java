package com.hanseongge.domain.marketprice.provider;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;

import java.util.List;

/**
 * 살 때·팔 때를 <b>각각</b> 고시하는 원천.
 *
 * <h2>기본 계약과 무엇이 다른가</h2>
 * {@link MarketPriceProvider#fetch(MetalType)} 은 금속 하나에 값 하나를 돌려준다.
 * 그 계약은 "국제 기준가 하나 + 우리 마진" 모델의 원천(KRX·공공데이터포털 등)에 맞는다.
 *
 * <p>소매 고시가를 주는 원천은 다르다. 살 때와 팔 때가 <b>애초에 별개의 숫자</b>이고
 * 둘 사이 간격도 날마다 흔들린다(고정 마진이 아니다). 한쪽에서 다른 쪽을 계산해 낼 수 없다.
 *
 * <h2>왜 인터페이스로 남겨 두는가</h2>
 * 구현체는 지금 없다. 그래도 이 계약을 두는 이유는, 수집기가 <b>두 값을 함께 받아
 * 짝으로 발행하는 경로</b>를 이미 갖추고 있기 때문이다. 그 경로는 특정 공급자의
 * 사정이 아니라 "금값이 움직이면 살 때·팔 때가 같이 움직인다"는 원칙에서 나온 것이라,
 * 공급자가 바뀌어도 그대로 쓰인다.
 *
 * <p>공급자가 정해지면 이 인터페이스만 구현하면 된다. 수집기·발행·보류 판단은
 * 손대지 않는다.
 */
public interface SidedMarketPriceProvider extends MarketPriceProvider {

    /**
     * 살 때·팔 때를 <b>한 번의 요청으로</b> 가져온다.
     *
     * <p>금속마다 따로 부르지 않도록 구현할 것. 응답 한 건에 여러 금속이 들어 있는
     * 원천에서 금속별로 요청하면 같은 데이터를 여러 번 받는다 — 우리 쪽 낭비이자
     * 상대 서버에 대한 결례다.
     *
     * @return 비어 있으면 "가져올 새 값이 없음"이다. 실패는
     *         {@link MarketPriceFetchException} 으로 던진다
     */
    List<SidedPrice> fetchBothSides(MetalType metal);

    /** 어느 쪽 값인지 함께 들고 다니는 원본. */
    record SidedPrice(PriceSide side, RawPrice raw) {
    }
}
