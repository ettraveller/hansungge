package com.hanseongge.domain.marketprice.entity;

/**
 * 관리자 보정 방식 (개발계획 §12.2 L2). V2 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <pre>
 * AUTO         최종 = 원본
 * FIXED_ADJ    최종 = 원본 + adj_amount        (요구사항 예시: 200,000 + 10,000 = 210,000)
 * PERCENT_ADJ  최종 = 원본 × (1 + adj_percent/100)
 * MANUAL       최종 = manual_price (관리자 지정가 오버라이드)
 * </pre>
 *
 * <p>{@code DERIVED}(18K/14K 자동 환산)는 V2 CHECK 제약에 없어 아직 값으로 두지 않는다.
 * 파생 비율 자체도 확정 대기(TODO A-7)라 지금 넣으면 쓸 수 없는 값이 된다.
 */
public enum PolicyMode {

    AUTO("보정 없음"),
    FIXED_ADJ("정액 보정"),
    PERCENT_ADJ("정률 보정"),
    MANUAL("최종가 지정");

    private final String displayName;

    PolicyMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
