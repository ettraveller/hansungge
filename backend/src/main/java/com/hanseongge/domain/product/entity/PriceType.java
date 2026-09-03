package com.hanseongge.domain.product.entity;

/**
 * 상품 가격 산정 방식. V3 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p><b>상품 테이블에 "현재 판매가"는 저장되지 않는다</b>(V3 헤더 주석). 시세 연동
 * 상품의 가격은 조회할 때마다 계산되며, 그래서 시세가 바뀌면 상품 가격이 자동으로 바뀐다.
 * {@link #FIXED} / {@link #MANUAL} 만 예외적으로 저장된 값을 쓴다.
 */
public enum PriceType {

    /** 고정가. 시세를 참조하지 않는다 */
    FIXED("고정가", false),
    /** 관리자 지정가. 시세를 참조하지 않는다 */
    MANUAL("관리자 지정가", false),

    /** 시세 × 중량 + 공임 */
    MARKET_LINKED("시세 연동", true),
    /** 시세 × 중량 + 공임 + <b>옵션 추가공임</b> */
    MARKET_PLUS("시세 연동 + 옵션공임", true),
    /** (시세 × 중량) × 배수 + 공임 */
    MARKET_MULTIPLIER("시세 연동 × 배수", true);

    private final String displayName;
    private final boolean marketLinked;

    PriceType(String displayName, boolean marketLinked) {
        this.displayName = displayName;
        this.marketLinked = marketLinked;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 시세를 참조하는가.
     *
     * <p>true 인 상품만 "시세문의" 폴백 대상이다 — 고정가 상품은 시세가 없어도 팔 수 있다.
     */
    public boolean isMarketLinked() {
        return marketLinked;
    }

    /** 옵션의 추가 공임을 반영하는가 (설계서 §7.1 — MARKET_PLUS 만 해당). */
    public boolean usesOptionAdditionalLabor() {
        return this == MARKET_PLUS;
    }
}
