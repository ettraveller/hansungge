package com.hanseongge.domain.payment.entity;

/** 결제 상태 ({@code payments.status} CHECK 와 같은 값이어야 한다). */
public enum PaymentStatus {
    /** 결제창을 띄웠다. 승인 결과를 모른다 */
    READY,
    APPROVED,
    FAILED,
    CANCELLED,
    PARTIAL_REFUNDED,
    REFUNDED
}
