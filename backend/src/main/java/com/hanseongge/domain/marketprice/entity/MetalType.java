package com.hanseongge.domain.marketprice.entity;

import java.math.BigDecimal;

/**
 * 금속 종류. V2 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p>기준 단위는 <b>KRW / 1g 고정</b>이다 (개발계획 §12.2). 돈(3.75g) 단위 입력은
 * 저장 <i>전에</i> g 로 환산한다 — 두 단위가 같은 컬럼에 섞이면 어느 행이 어느 단위인지
 * 사후에 판별할 방법이 없다.
 *
 * <p>{@code defaultMin/MaxPricePerGram} 은 <b>범위 검증용 안전망</b>이지 시세가 아니다.
 * 자릿수 실수(1,370,000 을 137,000 대신 입력)나 단위 오입력을 걸러내기 위한 넉넉한
 * 상하한이며, 운영 중 조정이 필요하면 {@code system_settings} 의
 * {@code MIN_PRICE_PER_GRAM_<금속>} / {@code MAX_PRICE_PER_GRAM_<금속>} 이 우선한다.
 */
public enum MetalType {

    GOLD_24K("순금 24K", 50_000, 500_000),
    GOLD_18K("18K", 30_000, 400_000),
    GOLD_14K("14K", 20_000, 300_000),
    SILVER("은", 500, 20_000),
    PLATINUM("백금", 20_000, 300_000);

    /** 1돈 = 3.75g. 단위 오입력 감지의 기준이기도 하다. */
    public static final BigDecimal GRAMS_PER_DON = new BigDecimal("3.75");

    private final String displayName;
    private final BigDecimal defaultMinPricePerGram;
    private final BigDecimal defaultMaxPricePerGram;

    MetalType(String displayName, long defaultMin, long defaultMax) {
        this.displayName = displayName;
        this.defaultMinPricePerGram = BigDecimal.valueOf(defaultMin);
        this.defaultMaxPricePerGram = BigDecimal.valueOf(defaultMax);
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal defaultMinPricePerGram() {
        return defaultMinPricePerGram;
    }

    public BigDecimal defaultMaxPricePerGram() {
        return defaultMaxPricePerGram;
    }

    /** {@code MIN_PRICE_PER_GRAM_GOLD_24K} 형태의 설정 키. */
    public String minSettingKey() {
        return "MIN_PRICE_PER_GRAM_" + name();
    }

    public String maxSettingKey() {
        return "MAX_PRICE_PER_GRAM_" + name();
    }
}
