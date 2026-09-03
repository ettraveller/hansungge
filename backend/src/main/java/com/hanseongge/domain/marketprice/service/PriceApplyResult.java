package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePending;

import java.math.BigDecimal;

/**
 * 시세 적용 시도의 결과.
 *
 * <p>두 가지로만 끝난다:
 * <ul>
 *   <li><b>발행</b> — {@code applied=true}, {@link #effective} 가 새 현재값</li>
 *   <li><b>보류</b> — {@code applied=false}, {@link #pending} 에 승인 대기건.
 *       이때 <b>현재 시세는 직전 값 그대로다</b></li>
 * </ul>
 *
 * <p>예외로 실패를 알리지 않는 이유: 보류는 오류가 아니라 <b>설계된 정상 경로</b>다
 * (개발계획 §12.4 "급격한 변동"). 예외로 던지면 호출부가 이를 장애처럼 다루게 되고,
 * 관리자에게 "승인이 필요합니다"가 아니라 "오류가 발생했습니다"가 표시된다.
 *
 * @param changePercent 직전 최종가 대비 변동률(%). 최초 발행이면 null
 */
public record PriceApplyResult(
        boolean applied,
        MarketPriceEffective effective,
        MarketPricePending pending,
        BigDecimal changePercent,
        PriceCalculation calculation
) {

    static PriceApplyResult applied(MarketPriceEffective effective, BigDecimal changePercent,
                                    PriceCalculation calculation) {
        return new PriceApplyResult(true, effective, null, changePercent, calculation);
    }

    static PriceApplyResult held(MarketPricePending pending, BigDecimal changePercent,
                                 PriceCalculation calculation) {
        return new PriceApplyResult(false, null, pending, changePercent, calculation);
    }
}
