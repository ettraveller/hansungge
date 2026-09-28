package com.hanseongge.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * PG 요청·응답 원문 ({@code payment_transactions}, append-only).
 *
 * <h2>왜 원문을 남기나</h2>
 * 결제 분쟁에서 "우리가 무엇을 보냈고 PG 가 무엇을 답했는가"를 재현할 수 있어야 한다.
 * 정리한 값만 남기면, 정작 다툼이 생기는 <b>예상하지 못한 필드</b>가 사라진다.
 *
 * <p>⚠️ 카드번호 같은 민감정보는 저장 전에 가린다. PG 응답에는 마스킹된 값이 오지만
 * 규격이 바뀔 수 있어 우리 쪽에서도 확인한다.
 *
 * <h2>{@code idempotencyKey} 가 중복 콜백을 막는다</h2>
 * 같은 승인이 두 번 도착하는 것은 PG 에서 정상적인 일이다(재시도). 유니크 제약에 걸리면
 * 이미 처리한 건이라는 뜻이므로, 오류가 아니라 <b>기존 결과를 그대로 돌려준다</b>.
 */
@Entity
@Getter
@Table(name = "payment_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "tx_type", nullable = false, length = 15)
    private String txType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String request;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String response;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "idempotency_key", length = 240)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static PaymentTransaction of(Long paymentId, String txType, String request,
                                        String response, boolean success, String idempotencyKey) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.paymentId = paymentId;
        tx.txType = txType;
        tx.request = request;
        tx.response = response;
        tx.success = success;
        tx.idempotencyKey = idempotencyKey;
        tx.createdAt = OffsetDateTime.now();
        return tx;
    }
}
