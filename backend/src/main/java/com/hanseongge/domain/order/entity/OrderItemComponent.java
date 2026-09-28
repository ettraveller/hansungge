package com.hanseongge.domain.order.entity;

import com.hanseongge.domain.pricing.service.ComponentLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 항목의 가격 구성요소 스냅샷 ({@code order_item_price_components}, V8 D-2).
 *
 * <h2>합계만 남기면 답할 수 없는 것</h2>
 * "이 목걸이 660,000원이 어떻게 나온 값인가" — 고객 문의, 부분 환불 산정, 세금계산서에서
 * 모두 필요하다. 상품 설정은 나중에 바뀌므로 그때 가서 다시 계산할 수 없다.
 *
 * <p><b>불변식: 이 행들의 {@code amount} 합 = {@code order_items.unit_price_before_discount}.</b>
 * DB 제약으로 걸지 않는다 — 행을 하나씩 넣는 중간 상태에서는 항상 깨지기 때문이다.
 * 대신 저장하는 쪽과 테스트가 지킨다.
 */
@Entity
@Getter
@Table(name = "order_item_price_components")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private int sort;

    @Column(name = "component_type", nullable = false, length = 20)
    private String componentType;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "calc_method", nullable = false, length = 20)
    private String calcMethod;

    @Column(name = "weight_g", precision = 12, scale = 4)
    private BigDecimal weightG;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 9, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean taxable;

    public static OrderItemComponent snapshot(Long orderItemId, ComponentLine line) {
        OrderItemComponent component = new OrderItemComponent();
        component.orderItemId = orderItemId;
        component.sort = line.sort();
        component.componentType = line.type().name();
        component.label = line.label();
        component.calcMethod = line.method().name();
        component.weightG = line.weight() == null ? null : line.weight().value();
        component.unitPrice = line.unitPricePerGram() == null ? null : line.unitPricePerGram().value();
        component.rate = line.rate();
        component.amount = line.amount().value();
        // 면세/과세 구분은 아직 상품 단위로 나뉘지 않는다 (A-1 미확정). 기본값을 그대로 쓴다.
        component.taxable = true;
        return component;
    }
}
