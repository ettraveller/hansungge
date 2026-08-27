package com.hanseongge.domain.product.entity;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.Weight;
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
 * 상품 ({@code products}).
 *
 * <h2>판매가 컬럼이 없다</h2>
 * V3 헤더가 못 박은 원칙이다 — <b>가격 = 시세 × 중량 + 공임</b> 이고, 계산은
 * {@code PriceCalculationService} 가 한다. 상품 테이블에 현재가를 저장하면 시세가 바뀔
 * 때마다 전 상품을 UPDATE 하는 배치가 필요해지고, 그 배치가 실패한 순간부터
 * 화면에 틀린 가격이 뜬다. {@code fixed_price} 는 {@link PriceType#FIXED} /
 * {@link PriceType#MANUAL} 전용이며 시세를 참조하지 않는 상품에만 쓰인다.
 *
 * <p>이 엔티티에 계산 메서드를 두지 않은 것도 같은 이유다. 가격은 시세·정책·부가세·
 * 반올림이 함께 결정하는 값이라 상품 하나가 답할 수 있는 질문이 아니다.
 */
@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Object Storage 키. URL 을 저장하지 않는다 — 스토리지가 바뀌면 전 행이 무효가 된다. */
    @Column(name = "thumbnail_key", length = 300)
    private String thumbnailKey;

    /** 시세 연동 상품만 필수. FIXED/MANUAL 이면 null 일 수 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20)
    private MetalType metalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", length = 20, nullable = false)
    private PriceType priceType;

    @Column(name = "weight_g", precision = 12, scale = 4)
    private BigDecimal weightG;

    /** 0.9999, 0.7500 등. 표시용이며 계산에는 쓰지 않는다 (순도는 metal_type 이 표현한다). */
    @Column(name = "purity", precision = 6, scale = 4)
    private BigDecimal purity;

    @Column(name = "labor_cost", precision = 15, scale = 2, nullable = false)
    private BigDecimal laborCost;

    /** MARKET_MULTIPLIER 전용. 기본 1. */
    @Column(name = "multiplier", precision = 8, scale = 4, nullable = false)
    private BigDecimal multiplier;

    /** FIXED/MANUAL 전용. */
    @Column(name = "fixed_price", precision = 15, scale = 2)
    private BigDecimal fixedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ProductStatus status;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Product(Long categoryId, String name, ProductPricing pricing,
                    String description, String thumbnailKey, String createdBy) {
        this.categoryId = categoryId;
        this.name = name;
        applyPricing(pricing);
        this.description = description;
        this.thumbnailKey = thumbnailKey;
        this.status = ProductStatus.HIDDEN;   // 등록 직후에는 노출하지 않는다 (아래 설명)
        this.active = true;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 상품 등록.
     *
     * <p><b>{@code HIDDEN} 으로 시작한다.</b> 등록 직후에는 옵션·재고·이미지가 아직 없고,
     * 시세 연동 상품이면 가격도 확인되지 않은 상태다. 그대로 노출되면 고객이 <b>재고 없는
     * 상품을 주문</b>하거나 잘못된 가격을 본다. 관리자가 미리보기로 확인한 뒤
     * {@link #changeStatus} 로 판매를 시작한다.
     */
    public static Product create(Long categoryId, String name, ProductPricing pricing,
                                 String description, String thumbnailKey, String createdBy) {
        return new Product(categoryId, name, pricing, description, thumbnailKey, createdBy);
    }

    public void update(Long categoryId, String name, ProductPricing pricing,
                       String description, String thumbnailKey, String updatedBy) {
        this.categoryId = categoryId;
        this.name = name;
        applyPricing(pricing);
        this.description = description;
        this.thumbnailKey = thumbnailKey;
        touch(updatedBy);
    }

    /**
     * 판매 상태 변경.
     *
     * <p>삭제는 없다. 상품을 지우면 그 상품을 담은 <b>과거 주문의 상품 정보가 사라진다.</b>
     * 판매 종료는 {@link ProductStatus#DISCONTINUED} 로 표현한다.
     */
    public void changeStatus(ProductStatus newStatus, String updatedBy) {
        this.status = newStatus;
        touch(updatedBy);
    }

    /** 노출 토글. {@code active=false} 는 상태와 무관하게 모든 공개 조회에서 제외된다. */
    public void changeActive(boolean active, String updatedBy) {
        this.active = active;
        touch(updatedBy);
    }

    private void applyPricing(ProductPricing pricing) {
        this.metalType = pricing.metalType();
        this.priceType = pricing.priceType();
        this.weightG = pricing.weightG();
        this.purity = pricing.purity();
        this.laborCost = pricing.laborCost();
        this.multiplier = pricing.multiplier();
        this.fixedPrice = pricing.fixedPrice();
    }

    private void touch(String updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 현재 가격 재료. 감사로그 before/after 스냅샷에 쓴다. */
    public ProductPricing pricing() {
        return new ProductPricing(metalType, priceType, weightG, purity, laborCost, multiplier, fixedPrice);
    }

    /** 고객에게 노출 가능한가. 숨김·판매종료·비활성은 조회 자체가 막힌다. */
    public boolean isVisible() {
        return active && status.isVisible();
    }

    /** 지금 주문을 받을 수 있는 상태인가. 재고는 옵션이 따로 판단한다. */
    public boolean isOnSale() {
        return active && status == ProductStatus.ON_SALE;
    }

    public Money laborCostMoney() {
        return Money.of(laborCost);
    }

    public Money fixedPriceMoney() {
        return fixedPrice == null ? null : Money.of(fixedPrice);
    }

    public Weight weight() {
        return weightG == null ? null : Weight.ofGram(weightG);
    }
}
