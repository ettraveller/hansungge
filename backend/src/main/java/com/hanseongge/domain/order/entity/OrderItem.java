package com.hanseongge.domain.order.entity;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.pricing.service.ComponentType;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
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
import java.util.Set;

/**
 * 주문 항목 = <b>주문 시점 가격 스냅샷</b> ({@code order_items}).
 *
 * <h2>왜 상품을 참조하지 않고 베껴 두나</h2>
 * 이 시스템의 상품 가격은 저장된 값이 아니라 <b>시세로 매번 만들어지는 값</b>이다. 주문이
 * 상품을 참조만 하면, 시세가 오른 다음 날 과거 주문을 열었을 때 그날의 금액이 아니라
 * <b>오늘 금액</b>이 보인다. 영수증이 매일 바뀌는 셈이다.
 *
 * <p>그래서 상품명·옵션명·중량·적용 시세·계산 정책까지 전부 복사한다. 상품이 삭제되거나
 * 이름이 바뀌어도 주문은 그대로 남는다 ({@code product_id} 에 FK 를 걸지 않은 이유이기도 하다).
 *
 * <h2>구성요소는 {@link OrderItemComponent} 로 따로 남긴다</h2>
 * 합계만 남기면 "이 660,000원이 어떻게 나온 값인가"에 답할 수 없다 (V8 D-2). 고객 문의·환불
 * 산정·세금계산서에서 반드시 필요해서, 계산에 쓰인 줄을 그대로 옮겨 적는다.
 */
@Entity
@Getter
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 재고를 돌려줄 곳. 이름으로 되찾지 않기 위해 id 를 남긴다 (V8 D-8). */
    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "option_name", length = 100)
    private String optionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20)
    private MetalType metalType;

    @Column(name = "weight_g", precision = 12, scale = 4)
    private BigDecimal weightG;

    /** 적용된 최종 시세(원/g). 시세 미연동 상품이면 null */
    @Column(name = "market_price", precision = 15, scale = 2)
    private BigDecimal marketPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal adjustment;

    @Column(name = "labor_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal laborCost;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tax;

    @Column(name = "unit_price_before_discount", precision = 15, scale = 2)
    private BigDecimal unitPriceBeforeDiscount;

    @Column(name = "final_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalUnitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "final_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "effective_price_id")
    private Long effectivePriceId;

    /** 계산에 쓰인 정책. 정책이 바뀌어도 이 주문의 근거는 남아야 한다. */
    @Column(name = "vat_mode", length = 12)
    private String vatMode;

    @Column(name = "rounding_policy", length = 30)
    private String roundingPolicy;

    @Column(name = "calculated_at")
    private OffsetDateTime calculatedAt;

    /**
     * 가격 산출 결과를 그대로 베껴 항목을 만든다.
     *
     * <p>{@code quote} 는 <b>주문을 만드는 그 순간</b> 다시 계산한 것이어야 한다.
     * 장바구니에 담을 때의 값을 넘기면 그 사이 시세 변동이 결제 금액에 반영되지 않는다.
     */
    public static OrderItem snapshot(Long orderId, Product product, ProductOption option,
                                     PriceQuote quote, int quantity) {
        OrderItem item = new OrderItem();
        item.orderId = orderId;
        item.productId = product.getId();
        item.optionId = option.getId();
        item.productName = product.getName();
        item.optionName = option.getName();
        item.metalType = product.getMetalType();
        item.weightG = option.getWeightG() != null ? option.getWeightG() : product.getWeightG();
        item.marketPrice = quote.appliedMarketPrice() == null
                ? null : quote.appliedMarketPrice().value();
        item.effectivePriceId = quote.effectivePriceId();

        // 아래 네 칸은 자주 쓰는 값을 미리 접어 둔 것이다 — 목록 화면이 구성요소 테이블을
        // 매번 훑지 않게 하려는 것뿐이고, 진짜 근거는 order_item_price_components 다.
        item.adjustment = sumOf(quote, ComponentType.MARGIN);
        item.laborCost = sumOf(quote, ComponentType.LABOR, ComponentType.MATERIAL,
                ComponentType.STONE, ComponentType.PACKAGING);
        item.discount = sumOf(quote, ComponentType.DISCOUNT).abs();
        item.tax = quote.vat() == null ? BigDecimal.ZERO : quote.vat().value();

        item.unitPriceBeforeDiscount = quote.subtotal() == null ? null : quote.subtotal().value();
        item.finalUnitPrice = quote.finalUnitPrice().value();
        item.quantity = quantity;
        // 반올림은 단가에서 이미 끝났다. 여기서 다시 하면 합계가 어긋난다.
        item.finalAmount = quote.totalFor(quantity).value();

        item.vatMode = quote.vatMode() == null ? null : quote.vatMode().name();
        item.roundingPolicy = quote.roundingPolicy() == null ? null : quote.roundingPolicy().name();
        item.calculatedAt = quote.priceQuotedAt();
        return item;
    }

    private static BigDecimal sumOf(PriceQuote quote, ComponentType... types) {
        Set<ComponentType> wanted = Set.of(types);
        return quote.components().stream()
                .filter(line -> wanted.contains(line.type()))
                .map(line -> line.amount().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
