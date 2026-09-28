package com.hanseongge.domain.product.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
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

    /**
     * 재고를 담는 기본 옵션인가 (결함 D-7).
     *
     * <p>상품당 정확히 1개다 — {@code ux_opt_default} 부분 유니크 인덱스가 강제한다.
     * "옵션 없는 상품"을 없애 재고 소스를 {@code product_options.stock} 한 곳으로
     * 고정하기 위한 표시이며, 이 값이 비어 있는 상품이 생기면 재고가 어디에도
     * 존재하지 않게 된다.
     *
     * <p>수정 API 로는 바뀌지 않는다. 기본 옵션을 옮기는 것은 재고 이동을 동반하는
     * 별도 작업이라, 이름·중량을 고치다가 실수로 딸려가면 안 된다.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    private ProductOption(Long productId, String name, BigDecimal weightG,
                          BigDecimal additionalLaborCost, int stock, int sort,
                          boolean isDefault) {
        this.productId = productId;
        this.name = name;
        this.weightG = weightG;
        this.additionalLaborCost = additionalLaborCost == null ? BigDecimal.ZERO : additionalLaborCost;
        this.stock = stock;
        this.sort = sort;
        this.active = true;
        this.isDefault = isDefault;
    }

    /** 추가 옵션. 기본 옵션은 상품당 하나뿐이라 여기서 만들 수 없다. */
    public static ProductOption create(Long productId, String name, BigDecimal weightG,
                                       BigDecimal additionalLaborCost, int stock, int sort) {
        return new ProductOption(productId, name, weightG, additionalLaborCost, stock, sort, false);
    }

    /**
     * 상품 등록 시 자동 생성되는 기본 옵션 (D-7).
     *
     * <p>이 경로로만 {@code is_default=true} 가 만들어진다. 상품당 1개 제약은 DB 가
     * 지키지만, 애초에 만들 수 있는 지점을 하나로 좁혀 두는 편이 안전하다.
     */
    public static ProductOption createDefault(Long productId, String name, BigDecimal weightG,
                                              BigDecimal additionalLaborCost, int stock, int sort) {
        return new ProductOption(productId, name, weightG, additionalLaborCost, stock, sort, true);
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

    /**
     * 주문이 재고를 가져간다 (STEP 13).
     *
     * <h2>왜 결제 승인이 아니라 주문서 생성에서 빼나</h2>
     * 결제 승인 때 빼면, 두 사람이 마지막 하나를 동시에 주문서에 담고 <b>둘 다 결제에
     * 성공</b>할 수 있다. 그때는 이미 고객 돈이 빠져나간 뒤라 한쪽에게 취소를 사과해야 한다.
     * 주문서를 만들 때 미리 가져가면 그 일이 생기지 않는다.
     *
     * <p>대신 결제하지 않고 떠난 주문이 재고를 물고 있게 된다. 그래서 취소·실패에서
     * {@link #restoreStock} 로 돌려준다.
     * ⚠️ 아직 <b>방치된 미결제 주문을 자동으로 정리하지 않는다</b> — 사람이 취소해야 풀린다
     * (TODO K).
     *
     * @throws BusinessException 남은 재고보다 많이 가져가려 할 때
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다: " + quantity);
        }
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stock -= quantity;
    }

    /** 주문 취소·결제 실패로 재고를 돌려준다. */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 1 이상이어야 합니다: " + quantity);
        }
        this.stock += quantity;
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
