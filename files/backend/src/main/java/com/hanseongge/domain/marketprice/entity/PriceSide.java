package com.hanseongge.domain.marketprice.entity;

/**
 * 매입/판매 구분. V2 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p><b>이 구분은 L1(원본)에 없고 L2(정책)부터 갈라진다.</b> KRX 시세는 하나의 시장가이고,
 * 매입가·판매가는 회사의 <i>정책</i>(마진)이기 때문이다 (개발계획 §12.2). 레거시 크롤러가
 * 마진을 코드 상수(+5% / -7%)로 박아둔 문제를 구조적으로 해소하는 지점이다.
 */
public enum PriceSide {

    /** 회사가 파는 가격 (고객 구매가) */
    SELL("판매"),
    /** 회사가 사는 가격 (고객 매입가) */
    BUY("매입");

    private final String displayName;

    PriceSide(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
