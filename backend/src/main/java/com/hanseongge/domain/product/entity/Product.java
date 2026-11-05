package com.hanseongge.domain.product.entity;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.global.util.VatMode;
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

    /**
     * 주문을 받아 개별 생산하는 상품인가 (V33).
     *
     * <p><b>청약철회</b>를 결정한다 — 주문제작은 제작이 시작되면 철회가 제한되고,
     * 그 사실을 <b>상품을 사기 전에</b> 알려야 제한이 성립한다 (전자상거래법 §17②①).
     * 고지 없이 제한하면 제한이 무효가 되어, 제작이 끝난 주문도 환불해야 한다.
     * 그래서 이 값은 표시용 꼬리표가 아니라 <b>고지의 근거</b>다.
     *
     * <p>2026-11-05(V41)부터 <b>배송기간은 여기서 갈리지 않는다.</b> 전에는 이 값
     * 하나로 「영업일 7일 이내」와 「최대 6주」가 갈렸는데, 같은 주문제작 안에서도
     * 제품마다 기간이 달랐고 선택이 틀린 상품은 지킬 수 없는 기간을 약속했다.
     * 기간은 {@link #deliveryNotice} 가 상품마다 받는다.
     */
    @Column(name = "made_to_order", nullable = false)
    private boolean madeToOrder;

    /**
     * 이 상품의 배송기간 문구 (V41). NULL 이면 화면이 기본 문구를 쓴다.
     *
     * <p>⚠️ 비워 두는 상품은 <b>상세 이미지에 기간이 적혀 있어야 한다.</b> PG 심사가
     * 상품페이지 내 기재를 요구하고, 전자상거래법 §13② 의 공급시기 고지이기도 하다.
     */
    @Column(name = "delivery_period", length = 100)
    private String deliveryPeriod;

    /** 이 상품의 택배사 (V41). NULL 이면 화면이 기본값(우체국택배)을 쓴다. */
    @Column(name = "courier", length = 40)
    private String courier;

    /**
     * 과세 상품인가 (V38).
     *
     * <p>이 가게가 파는 물건은 한 종류가 아니다 — 금지금(골드바·순금)은 면세 요건이
     * 있고, 가공한 주얼리는 과세다. 전역 설정 하나로는 둘을 동시에 맞출 수 없어
     * 상품마다 둔다.
     *
     * <p><b>과세여도 판매가는 바뀌지 않는다.</b> 표시가가 곧 받는 돈이고(부가세 포함),
     * 그 안에 얼마가 세금인지를 주문서·영수증에서 쪼개 보여 줄 뿐이다.
     *
     * <p>세율은 여기 두지 않는다 — 법이 정하는 값이지 상품의 성질이 아니다.
     * {@code system_settings.VAT_RATE} 하나를 쓴다.
     */
    @Column(name = "vat_taxable", nullable = false)
    private boolean vatTaxable;

    /**
     * 1회 주문 최소 수량 (V8).
     *
     * <p>골드바처럼 낱개 판매가 기본인 상품은 1이지만, 소액 부자재는 묶음으로만 파는
     * 경우가 있다. 지금은 등록 화면에서 값을 받지 않아 항상 기본값 1이다.
     */
    @Column(name = "min_qty", nullable = false)
    private int minQty;

    /**
     * 1회 주문 최대 수량 (V8). null 이면 제한 없음이며 장바구니 상한(99)이 적용된다.
     *
     * <p>귀금속은 단가가 커서 실수로 두 자리 수량을 넣으면 금액이 수천만 원이 된다.
     * 상품별로 막을 수 있게 자리를 마련해 두었다.
     */
    @Column(name = "max_qty")
    private Integer maxQty;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Product(Long categoryId, String name, ProductPricing pricing,
                    String description, String thumbnailKey, boolean madeToOrder,
                    boolean vatTaxable, DeliveryNotice delivery, String createdBy) {
        this.categoryId = categoryId;
        this.name = name;
        applyPricing(pricing);
        this.description = description;
        this.thumbnailKey = thumbnailKey;
        this.madeToOrder = madeToOrder;
        this.vatTaxable = vatTaxable;
        applyDelivery(delivery);
        this.status = ProductStatus.HIDDEN;   // 등록 직후에는 노출하지 않는다 (아래 설명)
        // DB 기본값과 같은 값을 명시한다. 엔티티로 INSERT 하면 컬럼 DEFAULT 가 쓰이지 않는다.
        this.minQty = 1;
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
                                 String description, String thumbnailKey,
                                 boolean madeToOrder, boolean vatTaxable,
                                 DeliveryNotice delivery, String createdBy) {
        return new Product(categoryId, name, pricing, description, thumbnailKey,
                madeToOrder, vatTaxable, delivery, createdBy);
    }

    /**
     * ⚠️ {@code thumbnailKey} 를 받지 않는다. 그 값의 주인은 대표이미지 목록이고,
     * {@code ProductService.replaceImages(GALLERY)} 가 첫 장으로 맞춘다 (V31).
     *
     * <p>여기서 함께 받으면 상품 정보를 저장할 때마다 폼에 없는 값이 null 로 덮어써서
     * 목록의 사진이 사라진다 — 사진을 지운 적이 없는데 사라지므로 원인을 찾기 어렵다.
     */
    public void update(Long categoryId, String name, ProductPricing pricing,
                       String description, boolean madeToOrder, boolean vatTaxable,
                       DeliveryNotice delivery, String updatedBy) {
        this.categoryId = categoryId;
        this.name = name;
        applyPricing(pricing);
        this.description = description;
        this.madeToOrder = madeToOrder;
        this.vatTaxable = vatTaxable;
        applyDelivery(delivery);
        touch(updatedBy);
    }

    /**
     * 배송 고지를 덮어쓴다 (V41).
     *
     * <p>null 묶음도 받는다 — 이 필드를 보내지 않는 옛 호출이 있으면 "안 정했다"가
     * 되어 화면이 기본 문구를 쓴다. 조용히 빈 칸이 찍히는 것보다 낫다.
     */
    private void applyDelivery(DeliveryNotice delivery) {
        DeliveryNotice notice = delivery == null ? DeliveryNotice.NONE : delivery;
        this.deliveryPeriod = notice.period();
        this.courier = notice.courier();
    }

    /** 대표이미지 목록의 첫 장. 비면 null — 빈 문자열은 "사진이 있다"로 읽힌다. */
    public void changeThumbnailKey(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
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

    /**
     * 이 상품에 적용할 부가세 방식.
     *
     * <p>과세면 {@link VatMode#INCLUDED} — 표시가 안에 세금이 들어 있다고 보고
     * 공급가액과 세액을 쪼갠다. <b>최종 금액은 소계와 같다.</b> 면세면
     * {@link VatMode#NONE} 이고 세액은 0 이다.
     *
     * <p>{@code EXCLUDED}(별도 부과)는 쓰지 않는다. 그 방식이면 과세 상품 가격이
     * 전부 10% 올라 보이는데, 이 가게의 고시가는 받을 돈 그대로다.
     */
    public VatMode vatMode() {
        return vatTaxable ? VatMode.INCLUDED : VatMode.NONE;
    }

    /**
     * 카드·간편결제로 팔 수 있는 상품인가. <b>아니면 무통장입금 전용이다.</b>
     *
     * <h2>컬럼이 아니라 파생값인 이유</h2>
     * 토스페이먼츠 입점 심사(2026-11-03 회신)가 아래를 카드 결제에서 제외했다.
     * 카드사가 온라인 거래를 제한하는 품목이라 심사 자체가 통과되지 않는다.
     *
     * <ul>
     *   <li>순금(24K)·순은·골드바 등 가공되지 않은 '바' 형태 — 금종으로 걸러진다</li>
     *   <li>24K 상품 전부</li>
     *   <li>시세에 연동되어 판매가가 변동하는 상품 — 고정금액이 아니면 입점 불가</li>
     * </ul>
     *
     * <p>이것을 상품마다 고르는 칸으로 두면, 골드바 하나를 「카드 가능」으로 잘못
     * 등록하는 순간 심사 위반이 된다. 그래서 금종과 가격방식에서 <b>파생</b>시킨다 —
     * 운영자가 만질 수 있는 자리가 없다. 토스에 약속한 내용이 바로 그것이다.
     *
     * <p>{@code metalType} 이 없는 상품(금속을 적지 않은 기타 상품)은 무통장 전용으로
     * 떨어진다. 모르는 것을 허용 쪽으로 기울이지 않는다 — 틀렸을 때 잃는 것이
     * 입점 자격이다.
     *
     * <p>백금은 허용하지 않는다. 토스가 열어 준 범위는 「금·은을 가공한 주얼리,
     * 14K·18K 까지」이고 백금은 그 안에 없다.
     */
    public boolean isCardPayable() {
        if (metalType != MetalType.GOLD_18K && metalType != MetalType.GOLD_14K) {
            return false;
        }
        return !priceType.isMarketLinked();
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
