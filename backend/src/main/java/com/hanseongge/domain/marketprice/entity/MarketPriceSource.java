package com.hanseongge.domain.marketprice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시세 소스 레지스트리 ({@code market_price_sources}, 개발계획 §12.2 L0).
 *
 * <p>V2 가 {@code MANUAL}(관리자 수동 고시) / {@code KRX}(자동 수집) 두 행을 시드한다.
 * {@code KRX} 는 약관 확인(TODO B-1) 전까지 수집기를 비활성으로 운용한다.
 *
 * <p>행을 코드가 만들지 않는다. 소스가 늘어나는 것은 마이그레이션으로 다룰 일이지
 * 런타임에 벌어질 일이 아니다.
 */
@Entity
@Getter
@Table(name = "market_price_sources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPriceSource {

    /** 관리자 입력 원천. 지금 원본을 만드는 유일한 원천이다 (V19 가 나머지를 비활성화했다). */
    public static final String CODE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 20, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** 동시각 다중 소스 시 우선순위. 낮을수록 우선. */
    @Column(name = "priority", nullable = false)
    private int priority;
}
