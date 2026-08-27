package com.hanseongge.domain.product.entity;

import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.Weight;
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
 * 상품 옵션 ({@code product_options}) — 중량·추가공임·재고의 단위.
 *
 * <p>{@code weight_g} 가 있으면 <b>상품 중량보다 우선한다</b> (설계서 §7.1
 * "option weight 우선"). "3.75g(1돈) / 7.5g(2돈)" 처럼 같은 상품의 중량 변형을
 * 옵션으로 두는 것이 이 도메인의 기본 형태이기 때문이다.
 *
 * <p>V3 에는 옵션 없는 상품의 재고 필드가 없다(결함 D-7). 그래서 재고 판단은
 * "옵션이 있으면 옵션 재고, 없으면 재고 개념 없음"으로 동작한다 — D-7 이 해소되면
 * 기본 옵션 자동 생성으로 통일된다.
 */
@Entity
@Getter
@Table(name = "product_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** "3.75g (1돈)" 등 표시용. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** null 이면 상품 중량을 쓴다. */
    @Column(name = "weight_g", precision = 12, scale = 4)
    private BigDecimal weightG;

    /** MARKET_PLUS 에서만 가산된다 (설계서 §7.1). */
    @Column(name = "additional_labor_cost", precision = 15, scale = 2, nullable = false)
    private BigDecimal additionalLaborCost;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "sort", nullable = false)
    private int sort;

    @Column(name = "active", nullable = false)
    private boolean active;

    private ProductOption(Long productId, String name, BigDecimal weightG,
                          BigDecimal additionalLaborCost, int stock, int sort) {
        this.productId = productId;
        this.name = name;
        this.weightG = weightG;
        this.additionalLaborCost = additionalLaborCost == null ? BigDecimal.ZERO : additionalLaborCost;
        this.stock = stock;
        this.sort = sort;
        this.active = true;
    }

    public static ProductOption create(Long productId, String name, BigDecimal weightG,
                                       BigDecimal additionalLaborCost, int stock, int sort) {
        return new ProductOption(productId, name, weightG, additionalLaborCost, stock, sort);
    }

    public void update(String name, BigDecimal weightG, BigDecimal additionalLaborCost,
                       int sort, boolean active) {
        this.name = name;
        this.weightG = weightG;
        this.additionalLaborCost = additionalLaborCost == null ? BigDecimal.ZERO : additionalLaborCost;
        this.sort = sort;
        this.active = active;
    }

    /**
     * 재고를 지정 수량으로 <b>덮어쓴다</b> (실사 반영).
     *
     * <p>주문에 의한 차감은 이 메서드가 아니라 STEP 13 의 {@code decreaseStock} 이 맡는다.
     * 둘을 같은 메서드로 두면 실사 입력이 주문 차감과 경합해 재고가 어긋난다.
     */
    public void adjustStock(int newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다: " + newStock);
        }
        this.stock = newStock;
    }

    public boolean isInStock() {
        return stock > 0;
    }

    public Weight weight() {
        return weightG == null ? null : Weight.ofGram(weightG);
    }

    public Money additionalLaborCostMoney() {
        return Money.of(additionalLaborCost);
    }

    public boolean hasAdditionalLaborCost() {
        return additionalLaborCost != null && additionalLaborCost.compareTo(BigDecimal.ZERO) != 0;
    }
}
