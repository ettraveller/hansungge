package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.global.util.PricePerGram;

import java.math.BigDecimal;

/**
 * 고시 한 줄 — 한 금속의 한쪽(살 때 또는 팔 때)이 <b>얼마에서 얼마로</b> 바뀌는가.
 *
 * <p>확인창에 보여줄 값과 실제로 발행할 값이 <b>같은 객체</b>에서 나온다. 따로 계산하면
 * 둘이 어긋날 수 있다 — 18K·14K 는 원 미만 반올림 방향까지 같아야 한다.
 *
 * @param currentPricePerGram 고시 전 값. 처음 고시하면 null
 * @param changePercent       고시 전 값 대비 변동률(%, 소수 둘째 자리). 처음이면 null
 * @param exceedsLimit        변동률이 확인 기준({@code MAX_PRICE_CHANGE_PERCENT})을 넘는가
 * @param deriveRatio         환산이면 비율. 직접 입력이면 null
 * @param basePricePerGram    환산이면 곱한 24K 값. 직접 입력이면 null
 */
public record QuoteLine(
        MetalType metalType,
        PriceSide priceSide,
        PricePerGram newPrice,
        BigDecimal currentPricePerGram,
        BigDecimal changePercent,
        boolean exceedsLimit,
        PolicyMode mode,
        BigDecimal deriveRatio,
        BigDecimal basePricePerGram
) {

    public boolean derived() {
        return mode == PolicyMode.DERIVED;
    }

    /** 고시 전 값과 같은가. 처음 고시면 거짓이다. */
    public boolean unchanged() {
        return currentPricePerGram != null && newPrice.value().compareTo(currentPricePerGram) == 0;
    }
}
