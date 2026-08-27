package com.hanseongge.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 1g 당 시세(KRW/g). DB 의 {@code NUMERIC(15,2)} 에 대응한다.
 *
 * <p>0 이하 값은 생성 자체가 불가능하다 — "시세가 0원이 되는 경로는 존재하지 않는다"는
 * 설계 원칙 P6 을 타입 수준에서 강제한다. 수집 실패는 이 객체를 만들지 못하고,
 * 만들지 못하면 최종 시세가 발행되지 않으며, 발행되지 않으면 기존 시세가 유지된다.
 */
public final class PricePerGram implements Comparable<PricePerGram> {

    public static final int SCALE = 2;

    private final BigDecimal value;

    private PricePerGram(BigDecimal value) {
        BigDecimal scaled = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("시세는 0보다 커야 합니다: " + scaled.toPlainString());
        }
        this.value = scaled;
    }

    public static PricePerGram of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return new PricePerGram(value);
    }

    public static PricePerGram of(long value) {
        return new PricePerGram(BigDecimal.valueOf(value));
    }

    public static PricePerGram of(String value) {
        return new PricePerGram(new BigDecimal(value));
    }

    /** 관리자 보정: 정액 가산 (policy mode = FIXED_ADJ). */
    public PricePerGram plusAmount(BigDecimal adjustment) {
        return new PricePerGram(this.value.add(adjustment));
    }

    /** 관리자 보정: 정률 가산 (policy mode = PERCENT_ADJ). {@code percent} 가 -7 이면 7% 차감. */
    public PricePerGram plusPercent(BigDecimal percent) {
        BigDecimal factor = BigDecimal.ONE.add(
                percent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        return new PricePerGram(this.value.multiply(factor));
    }

    /** 파생 시세: 24K → 18K/14K 환산 (policy mode = DERIVED). */
    public PricePerGram deriveBy(BigDecimal ratio) {
        return new PricePerGram(this.value.multiply(ratio));
    }

    /** 직전 시세 대비 변동률(%). 이상 변동 감지에 사용한다. */
    public BigDecimal changePercentFrom(PricePerGram previous) {
        return this.value.subtract(previous.value)
                .divide(previous.value, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public int compareTo(PricePerGram other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PricePerGram other)) return false;
        return this.value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString() + "원/g";
    }
}
