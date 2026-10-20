package com.hanseongge.domain.order.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
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
 * 주문 ({@code orders}).
 *
 * <h2>금액은 서버가 계산한 값만 들어온다</h2>
 * 화면이 보낸 금액을 받는 자리가 없다 (원칙 P1). 주문을 만들 때 장바구니의 각 줄을
 * <b>그 시점 시세로 다시 계산</b>해 합계를 낸다. 결제 승인 뒤에는 PG 가 알려준 승인 금액을
 * 이 값과 대조하고, 다르면 {@link #markPaid}가 거부한다.
 *
 * <h2>배송지를 주문에 복사해 둔다</h2>
 * 회원 정보를 참조만 하면 회원이 주소를 바꾼 순간 <b>과거 주문의 배송지까지 바뀐다.</b>
 * 어디로 보냈는지가 사라지면 분쟁에서 아무것도 증명할 수 없다.
 */
@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 고객·PG·관리자가 함께 쓰는 사람이 읽는 번호. 예: {@code HS20260928-000123} */
    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 30)
    private String receiverPhone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(name = "order_memo", length = 300)
    private String orderMemo;

    /**
     * 청약철회 제한 안내에 동의한 시각. NULL 이면 동의를 받지 않고 만들어진 주문이다.
     *
     * <p>지나간 주문을 소급해 채우지 않는다 — 받은 적 없는 동의를 받았다고 적는 것이다.
     */
    @Column(name = "withdrawal_consent_at")
    private OffsetDateTime withdrawalConsentAt;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Order create(String orderNo, Long userId,
                               BigDecimal itemsTotal, BigDecimal shippingFee,
                               String receiverName, String receiverPhone,
                               String zipcode, String address1, String address2, String memo,
                               boolean withdrawalConsent) {
        Order order = new Order();
        order.orderNo = orderNo;
        order.userId = userId;
        order.status = OrderStatus.ORDER_CREATED;
        order.shippingFee = shippingFee;
        order.discountAmount = BigDecimal.ZERO;
        order.totalAmount = itemsTotal.add(shippingFee);
        order.receiverName = receiverName;
        order.receiverPhone = receiverPhone;
        order.zipcode = zipcode;
        order.address1 = address1;
        order.address2 = address2;
        order.orderMemo = memo;
        order.orderedAt = OffsetDateTime.now();
        order.withdrawalConsentAt = withdrawalConsent ? order.orderedAt : null;
        order.updatedAt = order.orderedAt;
        return order;
    }

    /** 이 주문이 이 회원의 것인가. 남의 주문번호를 넣어 조회하는 경로를 막는다. */
    public void requireOwnedBy(Long userId) {
        if (!this.userId.equals(userId)) {
            // 404 로 답한다. 403 은 "그 번호의 주문이 있긴 하다"를 알려준다.
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    public void markPaymentPending() {
        if (!status.isPayable()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        this.status = OrderStatus.PAYMENT_PENDING;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 결제 완료로 넘긴다.
     *
     * @param approvedAmount PG 가 실제로 승인한 금액. 주문 합계와 다르면 거부한다
     * @throws BusinessException 금액이 다르거나 결제할 상태가 아닐 때
     */
    public void markPaid(BigDecimal approvedAmount) {
        if (status == OrderStatus.PAID) {
            // 같은 승인이 두 번 도착하는 것은 정상이다 (콜백 재시도). 조용히 넘긴다.
            return;
        }
        if (!status.isPayable()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        if (approvedAmount == null || approvedAmount.compareTo(totalAmount) != 0) {
            // 여기서 막지 않으면 화면에서 금액을 바꿔 부른 요청이 그대로 주문이 된다.
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        if (!status.isCancellableByCustomer()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = OffsetDateTime.now();
    }
}
