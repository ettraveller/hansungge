package com.hanseongge.domain.marketprice.entity;

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
 * L2 — 관리자 보정 정책 ({@code market_price_policies}, 개발계획 §12.1).
 *
 * <p>{@code (metal_type, price_side)} 당 정확히 1행이며 <b>현재값만</b> 담는다.
 * V2 가 전 금속 × SELL/BUY 10행을 {@code MANUAL} 로 시드한다.
 *
 * <p>변경 이력은 {@code market_price_policy_history}(결함 D-4)가 맡을 예정이나 V2 에는
 * 아직 없다. 그 전까지는 정책 변경이 {@code audit_logs} 에 before/after 로 남고,
 * 적용 시점 정책은 {@link MarketPriceEffective#getPolicySnapshot()} 에 통째로 복사된다 —
 * 이력 테이블이 없어도 "이 시세가 어떤 정책으로 나왔나"는 언제나 답할 수 있다.
 */
@Entity
@Getter
@Table(name = "market_price_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPricePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20, nullable = false)
    private MetalType metalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_side", length = 4, nullable = false)
    private PriceSide priceSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 15, nullable = false)
    private PolicyMode mode;

    /** FIXED_ADJ: 원본 + 이 금액. 음수 가능(매입 마진). */
    @Column(name = "adj_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal adjAmount;

    /** PERCENT_ADJ: 원본 × (1 + 이 값/100). -7 이면 7% 차감. */
    @Column(name = "adj_percent", precision = 7, scale = 4, nullable = false)
    private BigDecimal adjPercent;

    /** MANUAL: 최종가 직접 지정. null 이면 오버라이드가 설정되지 않은 상태다. */
    @Column(name = "manual_price", precision = 15, scale = 2)
    private BigDecimal manualPrice;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 정책 변경. 모드에 무관한 필드까지 한꺼번에 덮어쓰는 이유는, 부분 갱신을 허용하면
     * "PERCENT_ADJ 로 바꿨는데 예전 adj_amount 가 남아 있는" 상태가 생기고
     * 그 값이 나중에 모드를 되돌릴 때 조용히 되살아나기 때문이다.
     */
    public void change(PolicyMode mode, BigDecimal adjAmount, BigDecimal adjPercent,
                       BigDecimal manualPrice, String updatedBy) {
        this.mode = mode;
        this.adjAmount = adjAmount == null ? BigDecimal.ZERO : adjAmount;
        this.adjPercent = adjPercent == null ? BigDecimal.ZERO : adjPercent;
        this.manualPrice = manualPrice;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    /** MANUAL 오버라이드가 실제로 설정되어 있는가. */
    public boolean hasManualPrice() {
        return manualPrice != null && manualPrice.compareTo(BigDecimal.ZERO) > 0;
    }
}
