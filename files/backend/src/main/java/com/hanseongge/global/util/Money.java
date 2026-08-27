package com.hanseongge.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 금액(KRW) 값 객체. DB 의 {@code NUMERIC(15,2)} 에 대응한다.
 *
 * <p>설계 원칙 P3 — 이 프로젝트에서 금액은 {@code double}/{@code float} 로 다루지 않는다.
 * 원시 타입 금액을 도메인에 노출하지 않기 위해 연산 헬퍼를 이 클래스에 모은다.
 *
 * <p>중간 계산에서는 반올림하지 않는다. 반올림은 최종 판매가 산출 때 단 한 번만
 * {@link #applyRounding(RoundingPolicy)} 로 적용한다 (설계서 §13.5) — 단계마다 반올림하면
 * 구성요소 합계와 최종가가 어긋나 주문 명세서가 맞지 않는다.
 */
public final class Money implements Comparable<Money> {

    /** DB NUMERIC(15,2) 와 일치시킨다. */
    public static final int SCALE = 2;

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return new Money(amount);
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    /** 시세(원/g) × 중량(g) — 가격 계산의 출발점. */
    public static Money fromGram(PricePerGram pricePerGram, Weight weight) {
        Objects.requireNonNull(pricePerGram, "pricePerGram");
        Objects.requireNonNull(weight, "weight");
        return new Money(pricePerGram.value().multiply(weight.value()));
    }

    public static Money sum(Money... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (Money v : values) {
            total = total.add(v.amount);
        }
        return new Money(total);
    }

    public Money plus(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor));
    }

    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** 비율 적용. {@code percent} 가 8 이면 8% 를 뜻한다. */
    public Money percentOf(BigDecimal percent) {
        return new Money(this.amount.multiply(percent)
                .divide(BigDecimal.valueOf(100), SCALE + 4, RoundingMode.HALF_UP));
    }

    public Money applyRounding(RoundingPolicy policy) {
        return new Money(policy.apply(this.amount));
    }

    public BigDecimal value() {
        return amount;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /** 금액 비교는 반드시 compareTo 로 한다 — BigDecimal#equals 는 scale 까지 보므로 오판한다. */
    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return this.amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
