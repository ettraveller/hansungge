package com.hanseongge.domain.payment.dto;

import com.hanseongge.domain.payment.entity.Payment;
import com.hanseongge.domain.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 관리자 결제 내역 한 줄.
 *
 * <p>주문이 아니라 <b>결제 시도</b>가 단위다. 한 주문에 시도가 여러 번일 수 있고
 * (결제창에서 나갔다가 다시 등), "돈이 실제로 오간 기록"을 주문 상태와 별개로
 * 봐야 하는 날이 온다 — 승인은 됐는데 주문이 미결제로 남은 사고를 찾는 화면이다.
 */
@Schema(name = "AdminPaymentSummaryResponse", description = "관리자 결제 내역 한 줄")
public record AdminPaymentSummaryResponse(

        @Schema(description = "결제 id") Long id,
        @Schema(description = "주문 id — 주문 상세로 가는 링크에 쓴다") Long orderId,
        @Schema(description = "주문번호", example = "HS20261117-000001") String orderNo,
        @Schema(description = "결제사", example = "TOSS") String provider,
        @Schema(description = "수단 — PG 승인 응답의 값. 무통장은 BANK_TRANSFER", example = "카드")
        String method,
        @Schema(description = "상태 코드", example = "APPROVED") PaymentStatus status,
        @Schema(description = "상태 문구", example = "승인") String statusLabel,
        @Schema(description = "금액") BigDecimal amount,
        @Schema(description = "PG 거래키. 무통장은 없다") String pgTid,
        @Schema(description = "승인 시각. 승인 전이면 null") OffsetDateTime approvedAt,
        @Schema(description = "시도 시각") OffsetDateTime createdAt
) {

    public static AdminPaymentSummaryResponse of(Payment payment, String orderNo) {
        return new AdminPaymentSummaryResponse(
                payment.getId(),
                payment.getOrderId(),
                orderNo,
                payment.getProvider(),
                payment.getMethod(),
                payment.getStatus(),
                label(payment.getStatus()),
                payment.getAmount(),
                payment.getPgTid(),
                payment.getApprovedAt(),
                payment.getCreatedAt());
    }

    private static String label(PaymentStatus status) {
        return switch (status) {
            case READY -> "시도";
            case APPROVED -> "승인";
            case FAILED -> "실패";
            case CANCELLED -> "취소";
            case PARTIAL_REFUNDED -> "부분환불";
            case REFUNDED -> "환불";
        };
    }
}
