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
    MARKET_MULTIPLIER("시세 연동 × 배수", true),

    /**
     * 값을 내걸지 않는다 — 문의로만 판다.
     *
     * <p>지금까지 "시세문의"는 <b>사고의 결과</b>였다. 시세가 없거나 오래되면 값을
     * 0 으로 만드는 대신 물러나는 자리(원칙 P6). 그런데 값을 일부러 안 내거는 상품도
     * 있다 — 고가 주문제작이나 상담이 먼저인 물건이 그렇다.
     *
     * <p>그 둘을 같은 화면 상태로 두면 구분이 안 된다. "시세가 끊겼는데 아무도
     * 모르는 상태"와 "원래 문의로만 파는 상품"이 똑같이 보이면, 앞의 장애를
     * 뒤의 정상으로 착각하고 넘어가게 된다. 그래서 <b>의도한 시세문의</b>를
     * 별도의 가격 방식으로 둔다.
     *
     * <p>시세를 참조하지 않는다 — 시세가 멀쩡해도 값을 내걸지 않기 때문이다.
     */
    INQUIRY("시세문의", false);

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
