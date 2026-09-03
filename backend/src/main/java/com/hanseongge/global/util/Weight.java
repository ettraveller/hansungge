package com.hanseongge.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 중량(gram) 값 객체. DB 의 {@code NUMERIC(12,4)} 에 대응한다.
 *
 * <p>이 시스템의 내부 중량 단위는 <b>항상 gram</b> 이다. 돈(錢) 단위 입력은
 * 저장 전에 {@link #ofDon(BigDecimal)} 로 환산한다 — 단위가 섞이면 가격이
 * 3.75배 어긋나는 사고로 이어진다 (설계서 §12.4 단위 오류 감지).
 */
public final class Weight implements Comparable<Weight> {

    /** DB NUMERIC(12,4) 와 일치시킨다. */
    public static final int SCALE = 4;

    /** 1돈 = 3.75g */
    public static final BigDecimal GRAM_PER_DON = new BigDecimal("3.75");

    public static final Weight ZERO = new Weight(BigDecimal.ZERO);

    private final BigDecimal gram;

    private Weight(BigDecimal gram) {
        this.gram = gram.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Weight ofGram(BigDecimal gram) {
        Objects.requireNonNull(gram, "gram");
        return new Weight(gram);
    }

    public static Weight ofGram(String gram) {
        return new Weight(new BigDecimal(gram));
    }

    public static Weight ofDon(BigDecimal don) {
        Objects.requireNonNull(don, "don");
        return new Weight(don.multiply(GRAM_PER_DON));
    }

    public Weight plus(Weight other) {
        return new Weight(this.gram.add(other.gram));
    }

    public Weight multiply(int quantity) {
        return new Weight(this.gram.multiply(BigDecimal.valueOf(quantity)));
    }

    /** 표시용 돈 환산. 저장에는 쓰지 않는다. */
    public BigDecimal toDon() {
        return gram.divide(GRAM_PER_DON, SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal value() {
        return gram;
    }

    public boolean isPositive() {
        return gram.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public int compareTo(Weight other) {
        return this.gram.compareTo(other.gram);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Weight other)) return false;
        return this.gram.compareTo(other.gram) == 0;
    }

    @Override
    public int hashCode() {
        return gram.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return gram.toPlainString() + "g";
    }
}
