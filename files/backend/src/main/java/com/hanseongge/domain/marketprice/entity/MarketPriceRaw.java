package com.hanseongge.domain.marketprice.entity;

import com.hanseongge.global.util.PricePerGram;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * L1 — 수집/입력 원본 ({@code market_price_raw}, 개발계획 §12.1).
 *
 * <h2>절대 원칙</h2>
 * <b>이 값은 저장된 뒤 어떤 경우에도 변하지 않는다.</b> 관리자가 보정을 몇 번 바꾸든,
 * 최종가를 몇 번 갱신하든 원본은 그대로 남는다. "원본과 관리자 수정 시세를 절대로 하나의
 * 값으로 덮어쓰지 않는다"는 요구사항이 <b>물리적으로 다른 테이블</b>로 구현된 것이 여기다.
 *
 * <p>그래서 이 클래스에는 가격을 바꾸는 메서드가 없다. 유일한 상태 변경은
 * {@link #markAnomaly()} 이며, 그것도 <b>가격이 아니라 판정 표시</b>를 바꾼다.
 *
 * <p>매입/판매 구분이 없는 이유: 원본에는 그 개념이 없다. 하나의 시장가일 뿐이고
 * 매입가·판매가는 L2(정책)에서 갈라진다.
 */
@Entity
@Getter
@Table(name = "market_price_raw")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPriceRaw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private MarketPriceSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20, nullable = false)
    private MetalType metalType;

    /** KRW / 1g 고정. 돈 단위 입력은 저장 전에 환산된다. */
    @Column(name = "price_per_gram", precision = 15, scale = 2, nullable = false)
    private BigDecimal pricePerGram;

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private RawStatus status;

    /** 응답 원문 JSONB. 분쟁 시 근거가 된다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    /** 수동 고시 시 관리자 login_id. 자동 수집이면 null. */
    @Column(name = "created_by", length = 60)
    private String createdBy;

    private MarketPriceRaw(MarketPriceSource source, MetalType metalType, PricePerGram pricePerGram,
                           OffsetDateTime collectedAt, String rawPayload, String createdBy) {
        this.source = source;
        this.metalType = metalType;
        // PricePerGram 을 거쳐야만 값이 들어온다 — 0 이하는 그 타입이 생성 자체를 거부한다.
        this.pricePerGram = pricePerGram.value();
        this.collectedAt = collectedAt;
        this.status = RawStatus.NORMAL;
        this.rawPayload = rawPayload;
        this.createdBy = createdBy;
    }

    /** 자동 수집 원본. */
    public static MarketPriceRaw collected(MarketPriceSource source, MetalType metalType,
                                           PricePerGram pricePerGram, OffsetDateTime collectedAt,
                                           String rawPayload) {
        return new MarketPriceRaw(source, metalType, pricePerGram, collectedAt, rawPayload, null);
    }

    /** 관리자 수동 고시 원본 (개발계획 §12.3 — 수동도 L1 을 반드시 통과한다). */
    public static MarketPriceRaw declared(MarketPriceSource source, MetalType metalType,
                                          PricePerGram pricePerGram, String adminLoginId,
                                          String rawPayload) {
        return new MarketPriceRaw(source, metalType, pricePerGram, OffsetDateTime.now(),
                rawPayload, adminLoginId);
    }

    /**
     * 이상 변동으로 판정. <b>가격은 건드리지 않는다.</b>
     *
     * <p>이상값이라도 원본은 보존한다 — 나중에 "그때 실제로 얼마가 들어왔는가"를
     * 되짚을 수 없으면 이상 판정이 옳았는지도 검증할 수 없다.
     */
    public void markAnomaly() {
        this.status = RawStatus.ANOMALY;
    }

    public PricePerGram price() {
        return PricePerGram.of(pricePerGram);
    }

    /**
     * 관리자가 직접 고시한 원본인가.
     *
     * <p>{@code source.getCode()} 로 판정하지 않는다. {@code source} 는 지연로딩이라
     * 트랜잭션 밖(컨트롤러·테스트)에서 부르면 {@code LazyInitializationException} 이 난다 —
     * 판정 하나 때문에 응답 전체가 500 이 되는 함정이다. 이 스키마에서
     * {@code created_by} 가 있다는 것과 수동 고시라는 것은 같은 뜻이므로 그 값을 본다.
     */
    public boolean isDeclaredByAdmin() {
        return createdBy != null && !createdBy.isBlank();
    }
}
