package com.hanseongge.domain.marketprice.entity;

import com.hanseongge.global.util.PricePerGram;
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
 * L4 — 이상 변동 승인 대기 ({@code market_price_pending}, 개발계획 §12.2).
 *
 * <p>변동률이 임계값을 넘으면 <b>최종 시세를 발행하지 않고</b> 이 큐에 넣는다.
 * 그동안 현재 시세는 <b>직전 값 그대로</b>다 — 이것이 "급격한 변동이 자동으로 가격에
 * 반영되지 않는다"의 구현이다. 승인해야만 적용된다.
 *
 * <p>V2 에는 {@code policy_snapshot} / {@code reject_reason} / metal×side 당 PENDING 1건
 * 부분 UNIQUE 인덱스가 없다. 그중 "중복 PENDING 방지"는 서비스가
 * {@code existsByMetalTypeAndPriceSideAndStatus} 로 대신 막는다 — DB 제약이 아니라
 * 응용 수준 방어이므로 동시 요청에서는 두 건이 생길 수 있고, 그때는 각각 승인/반려하면 된다.
 */
@Entity
@Getter
@Table(name = "market_price_pending")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPricePending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 보류된 계산의 근거 원본. */
    @Column(name = "raw_id", nullable = false)
    private Long rawId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20, nullable = false)
    private MetalType metalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_side", length = 4, nullable = false)
    private PriceSide priceSide;

    /** 정책 적용 결과 = 승인 시 발행될 값. */
    @Column(name = "calc_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal calcPrice;

    /** 보류 시점의 현재 시세. */
    @Column(name = "prev_price", precision = 15, scale = 2)
    private BigDecimal prevPrice;

    @Column(name = "change_percent", precision = 8, scale = 4)
    private BigDecimal changePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private PendingStatus status;

    @Column(name = "decided_by", length = 60)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private MarketPricePending(Long rawId, MetalType metalType, PriceSide priceSide,
                               BigDecimal calcPrice, BigDecimal prevPrice, BigDecimal changePercent) {
        this.rawId = rawId;
        this.metalType = metalType;
        this.priceSide = priceSide;
        this.calcPrice = calcPrice;
        this.prevPrice = prevPrice;
        this.changePercent = changePercent;
        this.status = PendingStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    public static MarketPricePending of(Long rawId, MetalType metalType, PriceSide priceSide,
                                        PricePerGram calcPrice, PricePerGram prevPrice,
                                        BigDecimal changePercent) {
        return new MarketPricePending(rawId, metalType, priceSide, calcPrice.value(),
                prevPrice == null ? null : prevPrice.value(), changePercent);
    }

    public void approve(String decidedBy) {
        requirePending();
        this.status = PendingStatus.APPROVED;
        decide(decidedBy);
    }

    public void reject(String decidedBy) {
        requirePending();
        this.status = PendingStatus.REJECTED;
        decide(decidedBy);
    }

    private void decide(String decidedBy) {
        this.decidedBy = decidedBy;
        this.decidedAt = OffsetDateTime.now();
    }

    /**
     * 이미 처리된 건을 다시 처리하지 못하게 막는다.
     *
     * <p>이 검사가 없으면 승인 버튼 두 번 클릭에 시세가 두 번 발행되고, 두 번째 발행은
     * 첫 번째를 곧바로 마감시킨다 — 이력에 의미 없는 행이 남고 변동 알림이 두 번 나간다.
     */
    private void requirePending() {
        if (this.status != PendingStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 승인건입니다: id=" + id + " status=" + status);
        }
    }

    public PricePerGram calculatedPrice() {
        return PricePerGram.of(calcPrice);
    }
}
