package com.hanseongge.domain.marketprice.entity;

/**
 * 원본 판정 표시. V8 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p>관리자 입력은 언제나 {@link #NORMAL} 로 들어온다. 나머지는 외부 수집 시절(V19 이전)
 * 행을 읽기 위해 남겨 둔 값이다.
 */
public enum RawStatus {

    NORMAL,
    /** 과거 자동 수집 때 이상 변동으로 표시된 행. 원본 값 자체는 그대로 보존되어 있다. */
    ANOMALY,
    /** 쓰이지 않는다. DB 제약(V8)이 넣지 못하게 막는다. */
    FAILED
}
