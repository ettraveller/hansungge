package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.FetchResult;

/**
 * 원본 시세 값 검증 결과.
 *
 * <p>실패를 예외가 아니라 값으로 돌려주는 이유: 수집 실패는 <b>기록해야 할 정상 흐름</b>이지
 * 프로그램 오류가 아니다. 실패 종류가 그대로 {@code market_price_fetch_logs.result} 가 되고,
 * 그 값이 대시보드의 수집 상태가 된다.
 *
 * @param valid  통과 여부
 * @param result 실패 종류. 통과면 {@link FetchResult#SUCCESS}
 * @param reason 사람이 읽을 사유. 통과면 null
 */
public record PriceValidation(boolean valid, FetchResult result, String reason) {

    private static final PriceValidation OK = new PriceValidation(true, FetchResult.SUCCESS, null);

    public static PriceValidation ok() {
        return OK;
    }

    public static PriceValidation invalid(String reason) {
        return new PriceValidation(false, FetchResult.INVALID_VALUE, reason);
    }
}
