package com.hanseongge.domain.pricing.service;

/**
 * 가격 산출 결과 상태 (개발계획 §13.4).
 *
 * <p><b>예외 대신 상태로 돌려주는 이유:</b> "시세가 없다", "품절이다"는 장애가 아니라
 * 정상적으로 발생하는 상황이다. 예외로 던지면 상품 목록 하나에 시세 없는 상품이
 * 섞였을 때 목록 전체가 500 이 된다. 상태로 두면 그 줄만 "시세문의"로 그려진다.
 */
public enum PriceStatus {

    /** 정상 산출. 가격 표시·장바구니·주문 모두 가능 */
    AVAILABLE,

    /**
     * 유효 시세가 없거나 노후 → <b>"시세문의"</b>.
     *
     * <p>가격을 표시하지 않고 장바구니·주문을 차단한다. 이 상태에서 금액 필드는
     * 0 이 아니라 <b>null</b> 이다 (개발계획 §12.4).
     */
    INQUIRY_ONLY,

    /** 재고 없음. 가격은 정상 산출되므로 금액은 표시하되 주문만 막는다 */
    SOLD_OUT,

    /** 숨김·판매종료·비활성 — 고객에게 노출되지 않아야 하는 상품 */
    NOT_FOR_SALE;

    /** 이 상태로 주문을 받을 수 있는가. */
    public boolean isOrderable() {
        return this == AVAILABLE;
    }

    /** 금액을 화면에 표시해도 되는가. */
    public boolean hasPrice() {
        return this == AVAILABLE || this == SOLD_OUT;
    }
}
