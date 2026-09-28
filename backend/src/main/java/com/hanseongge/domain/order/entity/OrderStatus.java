package com.hanseongge.domain.order.entity;

/**
 * 주문 상태 ({@code orders.status} CHECK 와 같은 값이어야 한다).
 *
 * <h2>결제 전 상태가 둘인 이유</h2>
 * {@link #ORDER_CREATED} 는 주문서를 만든 직후, {@link #PAYMENT_PENDING} 은 결제창을
 * 띄운 뒤다. 둘을 합치면 <b>"결제창까지 갔다가 돌아오지 않은 주문"</b>을 가려낼 수 없다 —
 * 그 주문들은 고객이 결제했는지 아닌지 사람이 PG 관리자 화면과 대조해야 한다.
 *
 * <h2>{@link #PAID} 로 가는 길은 하나뿐이다</h2>
 * 결제 승인 응답의 금액이 주문 합계와 같을 때만 넘어간다. 금액 대조 없이 넘기면
 * 화면에서 금액을 바꿔 부른 요청이 그대로 주문이 된다.
 */
public enum OrderStatus {

    /** 주문서 생성됨. 아직 결제창을 띄우지 않았다 */
    ORDER_CREATED,
    /** 결제창으로 보냈다. 승인 결과를 기다린다 */
    PAYMENT_PENDING,
    /** 결제 승인 완료 — 금액 대조까지 통과했다 */
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCEL_REQUESTED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED;

    /** 결제를 시도할 수 있는 상태인가. 이미 낸 주문에 또 결제창을 띄우지 않는다. */
    public boolean isPayable() {
        return this == ORDER_CREATED || this == PAYMENT_PENDING;
    }

    /** 고객이 스스로 취소할 수 있는 상태인가. 배송이 시작되면 사람이 처리한다. */
    public boolean isCancellableByCustomer() {
        return this == ORDER_CREATED || this == PAYMENT_PENDING;
    }

    public String label() {
        return switch (this) {
            case ORDER_CREATED -> "결제 대기";
            case PAYMENT_PENDING -> "결제 진행 중";
            case PAID -> "결제 완료";
            case PREPARING -> "상품 준비 중";
            case SHIPPED -> "배송 중";
            case DELIVERED -> "배송 완료";
            case CANCEL_REQUESTED -> "취소 요청";
            case CANCELLED -> "취소됨";
            case REFUND_PENDING -> "환불 진행 중";
            case REFUNDED -> "환불 완료";
        };
    }
}
