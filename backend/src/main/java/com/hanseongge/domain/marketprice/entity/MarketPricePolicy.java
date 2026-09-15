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
 * L2 — 금속×side 별 고시 방식 ({@code market_price_policies}).
 *
 * <p>{@code (metal_type, price_side)} 당 정확히 1행이며 <b>지금 설정만</b> 담는다.
 *
 * <h2>두 가지뿐이다</h2>
 * <ul>
 *   <li>{@link PolicyMode#DIRECT} — 관리자가 입력한 값이 곧 고시가</li>
 *   <li>{@link PolicyMode#DERIVED} — 24K 고시가에 비율을 곱한 값 (18K·14K 만)</li>
 * </ul>
 * 외부 시세에 보정을 얹던 방식은 V19 에서 걷어냈다. 받아올 값이 없으면 더하거나 뺄
 * 대상도 없다.
 *
 * <p>설정을 바꾼 기록은 {@code audit_logs} 에, 그 설정으로 나간 시세의 근거는
 * {@link MarketPriceEffective#getPolicySnapshot()} 에 남는다.
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

    /**
     * 환산 기준 금속. 지금은 24K 뿐이다.
     *
     * <p>직접 입력으로 돌려도 지우지 않는다. 다시 환산으로 켤 때 화면이 직전 비율을
     * 채워 주면, 매번 비율을 찾아 다시 적는 일이 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "derive_from", length = 20)
    private MetalType deriveFrom;

    /** 환산 비율. 예: 18K 0.7350 — 24K 220,800원이면 18K 162,288원 */
    @Column(name = "derive_ratio", precision = 6, scale = 4)
    private BigDecimal deriveRatio;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 관리자가 직접 입력한다. 비율은 남겨 둔다 — 다시 켤 때 쓴다. */
    public void useDirect(String updatedBy) {
        this.mode = PolicyMode.DIRECT;
        touch(updatedBy);
    }

    /** {@code base} 고시가에 {@code ratio} 를 곱해 낸다. 검증은 서비스와 DB 제약이 한다. */
    public void useDerived(MetalType base, BigDecimal ratio, String updatedBy) {
        this.mode = PolicyMode.DERIVED;
        this.deriveFrom = base;
        this.deriveRatio = ratio;
        touch(updatedBy);
    }

    public boolean isDerived() {
        return mode == PolicyMode.DERIVED;
    }

    private void touch(String updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }
}
