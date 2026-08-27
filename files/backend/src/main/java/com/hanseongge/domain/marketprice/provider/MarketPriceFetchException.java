package com.hanseongge.domain.marketprice.provider;

import com.hanseongge.domain.marketprice.entity.FetchResult;
import lombok.Getter;

/**
 * Provider 수집 실패.
 *
 * <p>{@link MarketPriceProvider#fetch}의 {@code Optional.empty()} 와 역할이 다르다:
 * <ul>
 *   <li>{@code empty} — 정상이지만 <b>새로 가져올 값이 없음</b> (장 마감 후 등)</li>
 *   <li>이 예외 — <b>실패</b>. 어떤 종류인지({@link FetchResult})가 그대로 수집 로그가 된다</li>
 * </ul>
 * 둘을 섞으면 "값이 없는 것"과 "장애"가 구분되지 않아 대시보드가 쓸모없어진다.
 *
 * <p>이 예외는 수집기가 잡아서 로그로 바꾼다. <b>밖으로 전파되어 시세를 바꾸는 일은 없다</b> —
 * 실패는 원본을 만들지 않고, 원본이 없으면 최종 시세도 발행되지 않는다.
 */
@Getter
public class MarketPriceFetchException extends RuntimeException {

    private final FetchResult result;
    private final Integer httpStatus;

    public MarketPriceFetchException(FetchResult result, String message) {
        this(result, message, null, null);
    }

    public MarketPriceFetchException(FetchResult result, String message, Integer httpStatus) {
        this(result, message, httpStatus, null);
    }

    public MarketPriceFetchException(FetchResult result, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.result = result;
        this.httpStatus = httpStatus;
    }
}
