package com.hanseongge.domain.marketprice.service;

import java.math.BigDecimal;

/**
 * 한 최종 시세 행의 <b>원본 · 보정 · 최종</b> 3값.
 *
 * <p>요구사항 "원본 시세와 관리자 보정 시세를 함께 보여준다"를 만족시키는 형태다.
 * V6/V7 보강(결함 D-11)이 들어가면 이 세 값이 {@code market_prices_effective} 한 행에
 * 그대로 들어가지만, V2 에서는 {@code base_raw_id} 로 원본을 되짚어 조립한다.
 *
 * @param rawPricePerGram 원본가. 원본을 찾을 수 없으면 null (수기 보정 등)
 * @param adjustment      {@code 최종 - 원본}. 원본을 모르면 null
 */
public record PriceBreakdown(
        BigDecimal rawPricePerGram,
        BigDecimal adjustment,
        BigDecimal pricePerGram
) {

    static PriceBreakdown of(BigDecimal rawPricePerGram, BigDecimal pricePerGram) {
        if (rawPricePerGram == null) {
            return new PriceBreakdown(null, null, pricePerGram);
        }
        return new PriceBreakdown(rawPricePerGram, pricePerGram.subtract(rawPricePerGram), pricePerGram);
    }
}
