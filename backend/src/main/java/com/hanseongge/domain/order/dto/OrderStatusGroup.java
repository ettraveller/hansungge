package com.hanseongge.domain.order.dto;

import com.hanseongge.domain.order.entity.OrderStatus;

import java.util.List;
import java.util.Set;

/**
 * 주문 상태 묶음 — 고객이 생각하는 단위.
 *
 * <p>고객은 {@code PAID} 와 {@code PREPARING} 을 구분하지 않는다. 둘 다 "돈 냈고 아직
 * 안 왔다"이다. 화면의 탭이 이 묶음이고, 서버가 같은 묶음으로 걸러야 탭의 숫자와
 * 목록이 맞는다.
 *
 * <p>묶음을 화면에만 두면 — 숫자는 서버 총계, 목록은 화면 필터가 되어 반드시 어긋난다.
 */
public enum OrderStatusGroup {

    /** 전체 */
    ALL(Set.of()),

    /** 결제 대기 — 아직 돈이 들어오지 않았다 */
    AWAITING_PAYMENT(Set.of(OrderStatus.ORDER_CREATED, OrderStatus.PAYMENT_PENDING)),

    /** 준비 중 — 결제됐고 아직 안 떠났다 */
    PREPARING(Set.of(OrderStatus.PAID, OrderStatus.PREPARING)),

    /** 배송 — 떠났거나 도착했다 */
    SHIPPING(Set.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED)),

    /** 취소·환불 */
    CANCELLED(Set.of(OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED,
            OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED));

    private final Set<OrderStatus> statuses;

    OrderStatusGroup(Set<OrderStatus> statuses) {
        this.statuses = statuses;
    }

    /** 질의에 넘길 상태들. ALL 이면 비어 있고, 그때는 조건을 걸지 않는다. */
    public List<OrderStatus> statuses() {
        return List.copyOf(statuses);
    }
}
