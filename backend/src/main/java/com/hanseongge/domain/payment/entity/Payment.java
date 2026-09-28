package com.hanseongge.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 결제 ({@code payments}).
 *
 * <h2>주문과 따로 두는 이유</h2>
 * 한 주문에 결제 시도가 여러 번 있을 수 있다 — 카드가 한도 초과로 거절되면 고객은 다른
 * 수단으로 다시 낸다. 주문에 결제 정보를 직접 달면 실패한 시도가 덮여 사라지고,
 * "왜 승인 문자는 왔는데 주문은 미결제인가" 같은 문의에 답할 수 없다.
 *
 * <p>{@code pg_tid}(토스 paymentKey)에는 <b>승인된 건만</b> 유니크 제약이 걸려 있다
 * ({@code ux_pay_tid_approved}). 같은 거래번호로 승인이 두 번 남는 일을 DB 가 막는다.
 */
@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String provider;

    /** 통합 결제창 안에서 고객이 고른 수단. 승인 응답을 받아야 알 수 있다 */
    @Column(length = 20)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "pg_tid", length = 200)
    private String pgTid;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Payment ready(Long orderId, String provider, BigDecimal amount) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.provider = provider;
        payment.status = PaymentStatus.READY;
        payment.amount = amount;
        payment.createdAt = OffsetDateTime.now();
        payment.updatedAt = payment.createdAt;
        return payment;
    }

    public void approve(String pgTid, String method, BigDecimal approvedAmount,
                        OffsetDateTime approvedAt) {
        this.pgTid = pgTid;
        this.method = method;
        this.amount = approvedAmount;
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt == null ? OffsetDateTime.now() : approvedAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }
}
