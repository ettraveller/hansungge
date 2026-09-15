package com.hanseongge.domain.marketprice.entity;

/**
 * 고시 방식. V19 마이그레이션의 CHECK 제약과 앞의 두 값이 일치해야 한다.
 *
 * <pre>
 * DIRECT   관리자가 입력한 값이 곧 고시가
 * DERIVED  24K 고시가 × 환산 비율 (18K·14K 만)
 * </pre>
 *
 * <h2>나머지 네 값은 과거 기록용이다</h2>
 * 외부 시세를 받아 보정하던 시절(V14~V18)의 방식이다. 새로 설정할 수 없지만
 * {@code market_prices_effective.policy_mode} 에는 그대로 남아 있다 — 그때 그 방식으로
 * 계산됐다는 사실이고, 과거 주문 금액의 근거라 지우거나 고치지 않는다. 이 값들을
 * 없애면 이력 화면이 옛 행을 읽지 못한다.
 */
public enum PolicyMode {

    DIRECT("직접 입력"),
    DERIVED("24K 환산"),

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

    /** V19 이전 기록에만 있는 값인가. 이 값으로는 설정할 수 없다. */
    public boolean isLegacy() {
        return this != DIRECT && this != DERIVED;
    }
}
