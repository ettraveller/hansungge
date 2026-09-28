package com.hanseongge.domain.payment.service;

/**
 * PG 승인 실패.
 *
 * <p>서비스 계층이 {@code PAYMENT_APPROVAL_FAILED} 로 바꿔 내보낸다. 공급자 응답 원문에는
 * 가맹점 정보가 섞여 있어 사용자 응답에 담지 않는다 — 원문은 로그에만 남긴다.
 */
public class PaymentApprovalException extends RuntimeException {

    public PaymentApprovalException(String message, Throwable cause) {
        super(message, cause);
    }
}
