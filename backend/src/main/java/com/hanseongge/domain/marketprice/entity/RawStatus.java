package com.hanseongge.domain.marketprice.entity;

/**
 * 원본 시세 상태. V2 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p>{@link #FAILED} 는 <b>V2 제약에는 있으나 이 코드가 만들지 않는다.</b>
 * {@code price_per_gram > 0} CHECK 와 모순되기 때문이다 — 실패에는 가격이 없는데
 * 컬럼은 양수를 요구한다(결함 D-3). 수집 실패는 원본이 아니라
 * {@link MarketPriceFetchLog} 에 남는다.
 */
public enum RawStatus {

    NORMAL,
    /** 이상 변동으로 감지됨. <b>원본 값 자체는 그대로 보존된다.</b> */
    ANOMALY,
    /** @deprecated 사용하지 않는다. 실패는 {@link MarketPriceFetchLog} 로 간다 (D-3). */
    @Deprecated
    FAILED
}
