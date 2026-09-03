package com.hanseongge.domain.cart.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
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
import java.time.OffsetDateTime;

/**
 * 장바구니 한 줄 ({@code cart_items}).
 *
 * <h2>{@code price_at_add} 는 결제 금액이 아니다</h2>
 * 담은 시점의 단가를 <b>비교용으로만</b> 보관한다. 장바구니를 열 때마다 현재 시세로 다시
 * 계산해 이 값과 견주고, 달라졌으면 "담을 때보다 ○○원 올랐습니다"를 보여준다
 * (개발계획 §13.6). 주문 금액은 주문 시점에 서버가 또 한 번 계산한다 — 이 값을 그대로
 * 쓰는 경로는 어디에도 없다 (원칙 P1).
 *
 * <p>그래서 이 컬럼이 낡아도 사고가 나지 않는다. 낡았다는 사실 자체가 화면에 표시할
 * 정보이지, 결제에 쓰일 값이 아니기 때문이다.
 */
@Entity
@Getter
@Table(name = "cart_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    /**
     * 한 줄에 담을 수 있는 최대 수량.
     *
     * <p>상품별 {@code max_qty} 가 있으면 그쪽이 우선한다. 이 값은 그마저 없을 때의
     * 상한이다 — 없으면 수량 입력란에 아무 숫자나 넣어 재고 검사와 계산을 흔들 수 있다.
     */
    public static final int MAX_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * 옵션 ID. <b>NOT NULL 이다.</b>
     *
     * <p>상품을 만들면 기본 옵션이 반드시 하나 생기므로(D-7) 옵션 없는 줄은 존재하지
     * 않는다. nullable 로 두면 {@code ux_cart_item} 이 중복을 막지 못한다 —
     * PostgreSQL 에서 NULL 끼리는 서로 다른 값이라 같은 상품이 두 줄로 담긴다.
     */
    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** 담은 시점 단가. 표시·비교 전용이다. */
    @Column(name = "price_at_add", precision = 15, scale = 2, nullable = false)
    private BigDecimal priceAtAdd;

    /** 담은 시점 근거 시세. 고정가 상품이면 null 이다. */
    @Column(name = "effective_price_id_at_add")
    private Long effectivePriceIdAtAdd;

    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private CartItem(Long cartId, Long productId, Long optionId, int quantity,
                     BigDecimal priceAtAdd, Long effectivePriceIdAtAdd) {
        this.cartId = cartId;
        this.productId = productId;
        this.optionId = optionId;
        this.quantity = quantity;
        this.priceAtAdd = priceAtAdd;
        this.effectivePriceIdAtAdd = effectivePriceIdAtAdd;
        OffsetDateTime now = OffsetDateTime.now();
        this.addedAt = now;
        this.updatedAt = now;
    }

    public static CartItem of(Long cartId, Long productId, Long optionId, int quantity,
                              BigDecimal priceAtAdd, Long effectivePriceIdAtAdd) {
        return new CartItem(cartId, productId, optionId, quantity, priceAtAdd, effectivePriceIdAtAdd);
    }

    /**
     * 수량 변경.
     *
     * <p>상한을 넘기면 조용히 잘라내지 않고 거절한다. 잘라내면 사용자는 자기가 넣은
     * 수량이 반영된 줄 알고 결제로 넘어간다.
     */
    public void changeQuantity(int newQuantity, int maxAllowed) {
        if (newQuantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1개 이상이어야 합니다.");
        }
        if (newQuantity > maxAllowed) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "최대 " + maxAllowed + "개까지 담을 수 있습니다.");
        }
        this.quantity = newQuantity;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 같은 줄에 다시 담을 때 — 수량을 더하고 기준가를 <b>지금 값으로 갱신</b>한다.
     *
     * <p>기준가를 처음 담은 값으로 붙들어 두면, 방금 담은 개수까지 "옛날 가격"으로
     * 비교되어 변동 안내가 실제와 어긋난다.
     */
    public void addQuantity(int delta, int maxAllowed, BigDecimal currentUnitPrice,
                            Long effectivePriceId) {
        changeQuantity(this.quantity + delta, maxAllowed);
        if (currentUnitPrice != null) {
            this.priceAtAdd = currentUnitPrice;
            this.effectivePriceIdAtAdd = effectivePriceId;
        }
    }
}
