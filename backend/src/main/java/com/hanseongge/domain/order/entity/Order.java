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

    /**
     * 동의한 환불규정 판 (V34). NULL 이면 그 전의 주문이다.
     *
     * <p>이 값이 있어야 "그때 그 고객이 읽고 동의한 바로 그 내용"을 댈 수 있다.
     * 규정이 개정되어도 지난 판은 지워지지 않으므로({@code refund_policies} append-only)
     * 이 참조는 영원히 유효하다.
     */
    @Column(name = "refund_policy_id")
    private Long refundPolicyId;

    /**
     * 고객이 고른 결제수단. 주문서를 만드는 시점에 정해진다.
     *
     * <p>{@code payments.method} 와 다르다 — 그쪽은 PG 가 승인 응답에 실어 준 값이라
     * 승인이 끝나야 안다. 무통장 주문에는 PG 가 끼지 않아 승인 전까지 {@code payments}
     * 행 자체가 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /**
     * 통장에 찍히는 입금자명. 무통장 주문에만 있다.
     *
     * <p>주문자 이름과 다를 수 있다 — 남편·자녀 이름으로 부치는 일이 흔하다. 이 값이
     * 없으면 들어온 돈이 어느 주문의 것인지 사람이 찾아야 한다.
     */
    @Column(name = "depositor_name", length = 60)
    private String depositorName;

    /**
     * 입금 안내 기한. <b>강제하지 않는다.</b>
     *
     * <p>지나도 서버는 아무것도 하지 않는다. 계좌를 열어 입금하려는 사이에 주문이
     * 사라지면 그 돈이 갈 곳을 잃고, 들어온 입금을 사람이 찾아 되돌려야 한다.
     * 기한이 지난 주문은 관리자 목록에 모아 보여 주고 취소는 사람이 누른다.
     */
    @Column(name = "deposit_due_at")
    private OffsetDateTime depositDueAt;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Order create(String orderNo, Long userId,
                               BigDecimal itemsTotal, BigDecimal shippingFee,
                               String receiverName, String receiverPhone,
                               String zipcode, String address1, String address2, String memo,
                               boolean withdrawalConsent, Long refundPolicyId,
                               PaymentMethod paymentMethod, String depositorName,
                               OffsetDateTime depositDueAt) {
        Order order = new Order();
        order.orderNo = orderNo;
        order.userId = userId;
        order.status = OrderStatus.ORDER_CREATED;
        order.paymentMethod = paymentMethod;
        order.depositorName = depositorName;
        order.depositDueAt = depositDueAt;
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
        order.refundPolicyId = refundPolicyId;
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

    /**
     * 관리자가 입금을 확인했다 — 무통장 주문을 결제 완료로 넘긴다.
     *
     * <p>{@link #markPaid} 와 달리 <b>금액을 받지 않는다.</b> PG 승인에는 "PG 가 실제로
     * 승인한 금액"이라는 바깥의 사실이 있어 대조할 것이 있지만, 여기서 대조할 상대는
     * 사람이 통장을 보고 누른 버튼뿐이다. 금액을 인자로 받으면 그 값은 결국 관리자
     * 화면이 보낸 숫자이고, 그것을 주문 금액과 맞춰 봐야 <b>자기 자신과 비교하는
     * 꼴</b>이다 — 막는 것처럼 보이지만 아무것도 막지 못한다.
     *
     * <p>덜 들어온 입금은 결제 완료로 넘길 일이 아니라 고객에게 연락할 일이다.
     * 그래서 「입금 확인」은 "주문 금액대로 들어왔다"는 사람의 선언이고,
     * 실제 입금액 대조는 통장에서 사람이 한다.
     *
     * @throws BusinessException 무통장 주문이 아니거나 이미 결제가 끝난 주문일 때
     */
    public void confirmDeposit() {
        if (paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw new BusinessException(ErrorCode.ORDER_NOT_BANK_TRANSFER);
        }
        if (status == OrderStatus.PAID) {
            // 두 사람이 같은 주문을 동시에 확인했을 수 있다. 두 번째는 조용히 넘긴다.
            return;
        }
        if (!status.isPayable()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 무통장 주문인가. 상태 문구와 관리자 화면이 갈린다. */
    public boolean isBankTransfer() {
        return paymentMethod == PaymentMethod.BANK_TRANSFER;
    }

    public void cancel() {
        if (!status.isCancellableByCustomer()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = OffsetDateTime.now();
    }

    // =================================================================
    // 배송 — 관리자가 움직인다
    // =================================================================

    /**
     * 상품 준비 중으로 넘긴다.
     *
     * <p>결제가 끝난 주문에만 쓴다. 되돌리는 경로는 두지 않는다 — 준비를 시작했다가
     * 멈추는 일은 취소나 환불로 처리할 일이지, 상태를 되감을 일이 아니다.
     */
    public void markPreparing() {
        if (status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "결제 완료 주문만 준비 중으로 바꿀 수 있다. 현재=" + status);
        }
        this.status = OrderStatus.PREPARING;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 발송 처리.
     *
     * <p>{@code PAID} 에서 바로 올 수 있다. 재고를 들고 있는 상품은 결제 확인 후 곧장
     * 보내는 경우가 많아, 준비 중을 반드시 거치게 하면 실제 일하는 순서와 어긋난다.
     *
     * <p>이미 배송 중이어도 거부하지 않는다 — 송장번호를 잘못 넣어 고치는 일이 생기고,
     * 그때 상태를 되감게 하면 더 위험하다. 바뀌는 것은 송장번호뿐이다.
     */
    public void markShipped() {
        if (status != OrderStatus.PAID && status != OrderStatus.PREPARING
                && status != OrderStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "발송할 수 없는 상태다. 현재=" + status);
        }
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 배송 완료.
     *
     * <p>고가 귀금속이라 수령 기록을 남긴다 — 분실·수령거부를 다툴 때 근거가 된다
     * (이용약관 제10조). 배송 중인 주문에만 쓴다.
     */
    public void markDelivered() {
        if (status != OrderStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "배송 중인 주문만 완료로 바꿀 수 있다. 현재=" + status);
        }
        this.status = OrderStatus.DELIVERED;
        this.updatedAt = OffsetDateTime.now();
    }
}
