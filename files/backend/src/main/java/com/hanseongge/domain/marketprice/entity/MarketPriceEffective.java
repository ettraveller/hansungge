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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * L3 — 최종 적용 시세 ({@code market_prices_effective}, 개발계획 §12.1).
 *
 * <h2>append-only = 이력 겸용</h2>
 * 시세를 바꾼다는 것은 <b>기존 행의 값을 고치는 것이 아니라</b>, 기존 행을
 * {@code effective_to} 로 마감하고 새 행을 넣는 것이다. 그래서 이력 테이블이 따로 없다.
 * 현재값은 {@code effective_to IS NULL} 인 행이고, 부분 UNIQUE 인덱스
 * {@code ux_mpe_current} 가 metal×side 당 1개임을 DB 수준에서 보장한다.
 *
 * <h2>DB 트리거와의 계약</h2>
 * {@code trg_mpe_guard} 는 DELETE 를 전면 금지하고, UPDATE 는
 * <b>{@code effective_to} 를 NULL → 값으로 마감하는 단 한 가지</b>만 허용한다.
 * 그래서 이 클래스가 제공하는 상태 변경 메서드도 {@link #close(OffsetDateTime)} 하나뿐이다.
 * {@code @DynamicUpdate} 를 붙인 이유도 같다 — 바뀌지 않은 컬럼까지 UPDATE 문에 실어
 * 트리거를 자극할 이유가 없다.
 *
 * <p>V2 에는 {@code raw_price_per_gram} / {@code adjustment_amount} 컬럼이 없다(결함 D-11).
 * 원본가는 {@link #getBaseRawId()} 로, 보정 내용은 {@link #getPolicySnapshot()} 으로
 * 복원한다 — 한 행에서 바로 읽히지 않을 뿐 정보가 사라지지는 않는다.
 */
@Entity
@Getter
@DynamicUpdate
@Table(name = "market_prices_effective")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPriceEffective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20, nullable = false)
    private MetalType metalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_side", length = 4, nullable = false)
    private PriceSide priceSide;

    @Column(name = "price_per_gram", precision = 15, scale = 2, nullable = false)
    private BigDecimal pricePerGram;

    /** 근거가 된 L1 원본. 연관관계 대신 ID 만 두어 append-only 행을 가볍게 유지한다. */
    @Column(name = "base_raw_id")
    private Long baseRawId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_mode", length = 15, nullable = false)
    private PolicyMode policyMode;

    /** 적용 당시 정책 전체 JSONB. 정책이 나중에 바뀌어도 이 시세의 근거는 남는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false)
    private String policySnapshot;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    /** NULL = 현재 적용값. */
    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "change_reason", length = 300, nullable = false)
    private String changeReason;

    @Column(name = "created_by", length = 60, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private MarketPriceEffective(MetalType metalType, PriceSide priceSide, PricePerGram pricePerGram,
                                 Long baseRawId, PolicyMode policyMode, String policySnapshot,
                                 OffsetDateTime effectiveFrom, String changeReason, String createdBy) {
        this.metalType = metalType;
        this.priceSide = priceSide;
        this.pricePerGram = pricePerGram.value();
        this.baseRawId = baseRawId;
        this.policyMode = policyMode;
        this.policySnapshot = policySnapshot;
        this.effectiveFrom = effectiveFrom;
        this.changeReason = changeReason;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * 새 최종 시세 발행.
     *
     * @param changeReason 필수 (개발계획 §6.3 — 모든 적용에 사유와 작성자를 남긴다)
     */
    public static MarketPriceEffective issue(MetalType metalType, PriceSide priceSide,
                                             PricePerGram pricePerGram, Long baseRawId,
                                             PolicyMode policyMode, String policySnapshot,
                                             OffsetDateTime effectiveFrom,
                                             String changeReason, String createdBy) {
        return new MarketPriceEffective(metalType, priceSide, pricePerGram, baseRawId,
                policyMode, policySnapshot, effectiveFrom, changeReason, createdBy);
    }

    /**
     * 이 행을 마감한다. 트리거가 허용하는 유일한 UPDATE 다.
     *
     * <p>이미 마감된 행을 다시 마감하려 하면 트리거가 예외를 던지므로, 여기서 먼저 막아
     * DB 예외 대신 도메인 언어로 실패하게 한다.
     */
    public void close(OffsetDateTime at) {
        if (this.effectiveTo != null) {
            throw new IllegalStateException(
                    "이미 마감된 시세입니다: id=" + id + " effectiveTo=" + effectiveTo);
        }
        this.effectiveTo = at;
    }

    public boolean isCurrent() {
        return effectiveTo == null;
    }

    public PricePerGram price() {
        return PricePerGram.of(pricePerGram);
    }
}
