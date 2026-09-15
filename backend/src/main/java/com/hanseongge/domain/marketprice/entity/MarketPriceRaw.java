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
 * L1 — 관리자가 입력한 원본 ({@code market_price_raw}).
 *
 * <h2>왜 발행 시세와 따로 두는가</h2>
 * 발행 시세({@link MarketPriceEffective})는 "지금 얼마에 파는가"이고, 이 행은
 * "누가 언제 무엇을 입력했는가"다. 입력값이 곧 고시가인 지금도 둘을 한 행으로 합치지
 * 않는다. 18K·14K 는 입력이 아니라 24K 입력에서 환산되어 나가므로, 발행 행만으로는
 * 사람이 친 숫자가 무엇이었는지 남지 않는다.
 *
 * <p><b>저장된 뒤 어떤 경우에도 변하지 않는다.</b> 그래서 이 클래스에는 가격을 바꾸는
 * 메서드가 없다.
 *
 * <p>V19 이전 행에는 외부 수집 원본도 섞여 있다({@code created_by} 가 비어 있는 행).
 * 지금은 만들어지지 않지만, 과거 시세의 근거라 그대로 둔다.
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

    /** KRW / 1g 고정. */
    @Column(name = "price_per_gram", precision = 15, scale = 2, nullable = false)
    private BigDecimal pricePerGram;

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt;

    /** 시세 기준 시각. 관리자 입력은 입력한 순간이 곧 기준시각이다. */
    @Column(name = "quote_at", nullable = false)
    private OffsetDateTime quoteAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private RawStatus status;

    /** 입력 당시의 기록 JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    /** 입력한 관리자 login_id. */
    @Column(name = "created_by", length = 60)
    private String createdBy;

    /** 살 때 값인가 팔 때 값인가. V14 이전 행은 비어 있다(양측 공통 기준가). */
    @Enumerated(EnumType.STRING)
    @Column(name = "price_side", length = 4)
    private PriceSide priceSide;

    private MarketPriceRaw(MarketPriceSource source, MetalType metalType, PriceSide priceSide,
                           PricePerGram pricePerGram, OffsetDateTime declaredAt,
                           String adminLoginId, String rawPayload) {
        this.source = source;
        this.metalType = metalType;
        this.priceSide = priceSide;
        // PricePerGram 을 거쳐야만 값이 들어온다 — 0 이하는 그 타입이 생성 자체를 거부한다.
        this.pricePerGram = pricePerGram.value();
        this.collectedAt = declaredAt;
        this.quoteAt = declaredAt;
        this.status = RawStatus.NORMAL;
        this.createdBy = adminLoginId;
        this.rawPayload = rawPayload;
    }

    /** 관리자가 입력한 값. 살 때·팔 때를 각각 한 행씩 남긴다. */
    public static MarketPriceRaw declared(MarketPriceSource source, MetalType metalType,
                                          PriceSide priceSide, PricePerGram pricePerGram,
                                          OffsetDateTime declaredAt, String adminLoginId,
                                          String rawPayload) {
        if (priceSide == null) {
            throw new IllegalArgumentException("priceSide 는 필수입니다");
        }
        return new MarketPriceRaw(source, metalType, priceSide, pricePerGram, declaredAt,
                adminLoginId, rawPayload);
    }

    public PricePerGram price() {
        return PricePerGram.of(pricePerGram);
    }

    /**
     * 관리자가 직접 입력한 원본인가.
     *
     * <p>{@code source.getCode()} 로 판정하지 않는다. {@code source} 는 지연로딩이라
     * 트랜잭션 밖(컨트롤러·테스트)에서 부르면 {@code LazyInitializationException} 이 난다.
     * 이 스키마에서 {@code created_by} 가 있다는 것과 관리자 입력이라는 것은 같은 뜻이다.
     */
    public boolean isDeclaredByAdmin() {
        return createdBy != null && !createdBy.isBlank();
    }
}
