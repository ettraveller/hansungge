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

import java.time.OffsetDateTime;

/**
 * L1-b — 시세 수집 시도 이력 ({@code market_price_fetch_logs}, V7 / 결함 D-3).
 *
 * <p>성공·실패를 모두 남긴다. 대시보드의 "수집 상태 / 마지막 성공 시각"이 여기서 나온다.
 *
 * <p><b>실패 행에는 {@code rawId} 가 없다.</b> 실패하면 원본을 만들지 않기 때문이고,
 * 원본이 없으면 최종 시세도 발행되지 않으므로 기존 시세가 유지된다. 이 테이블의 존재
 * 자체가 "수집 실패가 가격을 0원으로 만드는 경로는 없다"의 증거다.
 *
 * <p>append-only 다 ({@code trg_mpfl_guard}). 수집 이력을 고칠 수 있으면 장애 분석의
 * 근거가 되지 못한다.
 */
@Entity
@Getter
@Table(name = "market_price_fetch_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPriceFetchLog {

    /** message 컬럼 길이. 넘치면 DB 가 거부하므로 저장 전에 자른다. */
    public static final int MESSAGE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metal_type", length = 20)
    private MetalType metalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20, nullable = false)
    private FetchResult result;

    /** SUCCESS 일 때만 채워진다. */
    @Column(name = "raw_id")
    private Long rawId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "elapsed_ms")
    private Integer elapsedMs;

    @Column(name = "message", length = MESSAGE_MAX)
    private String message;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    private MarketPriceFetchLog(Long sourceId, MetalType metalType, FetchResult result, Long rawId,
                                Integer httpStatus, Integer elapsedMs, String message,
                                int attemptNo, OffsetDateTime startedAt) {
        this.sourceId = sourceId;
        this.metalType = metalType;
        this.result = result;
        this.rawId = rawId;
        this.httpStatus = httpStatus;
        this.elapsedMs = elapsedMs;
        this.message = cut(message);
        this.attemptNo = attemptNo;
        this.startedAt = startedAt;
    }

    public static MarketPriceFetchLog success(Long sourceId, MetalType metalType, Long rawId,
                                              int elapsedMs, int attemptNo, OffsetDateTime startedAt) {
        return new MarketPriceFetchLog(sourceId, metalType, FetchResult.SUCCESS, rawId,
                null, elapsedMs, null, attemptNo, startedAt);
    }

    public static MarketPriceFetchLog failure(Long sourceId, MetalType metalType, FetchResult result,
                                              Integer httpStatus, int elapsedMs, String message,
                                              int attemptNo, OffsetDateTime startedAt) {
        return new MarketPriceFetchLog(sourceId, metalType, result, null,
                httpStatus, elapsedMs, message, attemptNo, startedAt);
    }

    private static String cut(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MESSAGE_MAX ? value : value.substring(0, MESSAGE_MAX);
    }
}
