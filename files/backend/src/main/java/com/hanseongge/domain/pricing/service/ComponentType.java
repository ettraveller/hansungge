package com.hanseongge.domain.pricing.service;

/**
 * 가격 구성요소 종류 (개발계획 §13.2).
 *
 * <p>V3 의 {@code SIMPLE} 모델(골드바 등)도 내부적으로 이 구성요소로 정규화해서 계산한다.
 * 그래야 계산 로직·주문 Snapshot·테스트가 하나로 유지되고, {@code COMPONENT} 모델
 * (주얼리, 결함 D-1 해소 후)이 들어와도 <b>불러오는 곳만 바뀌고 계산은 그대로</b>다.
 */
public enum ComponentType {

    /** 금지금 원가 — 시세 × 중량 */
    METAL_BASE("금액"),
    /** 공임·세공비 */
    LABOR("공임"),
    /** 부자재 (체인, 잠금장치 등) */
    MATERIAL("부자재"),
    /** 스톤 (큐빅, 보석) */
    STONE("스톤"),
    /** 판매 마진·배수 가산 */
    MARGIN("마진"),
    /** 포장·감정서 */
    PACKAGING("포장"),
    /** 할인 (음수) */
    DISCOUNT("할인");

    private final String displayName;

    ComponentType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
