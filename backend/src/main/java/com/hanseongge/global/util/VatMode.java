package com.hanseongge.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;

/**
 * 부가세 표시/부과 정책.
 *
 * <p>운영 값은 코드가 아니라 {@code system_settings.VAT_MODE} 에서 읽는다.
 * {@link RoundingPolicy} 와 같은 구조다 — 정책이 바뀌어도 재배포 없이 설정만 바꾼다.
 *
 * <p><b>[TODO A-1 — 미확정]</b> 골드바(금지금) 면세 / 주얼리 과세 구분은 세무사 확인
 * 대기 중이다. 확정 전까지 {@link #NONE} 으로 임시 동작한다.
 *
 * <h2>왜 임시 기본값이 {@code NONE} 인가</h2>
 * 세 후보 중 <b>최종 금액을 바꾸지 않는 유일한 값</b>이기 때문이다.
 * <ul>
 *   <li>{@code EXCLUDED} 로 두면 구성요소 합계보다 10% 비싼 가격이 표시된다</li>
 *   <li>{@code INCLUDED} 로 두면 금액은 같지만 면세 상품에 부가세액이 있다고 표시된다</li>
 *   <li>{@code NONE} 은 {@code 최종 = 소계}, 부가세액 0 — 정책이 확정될 때까지
 *       <b>시스템이 임의로 만들어낸 숫자가 화면에 뜨지 않는다</b></li>
 * </ul>
 *
 * @see VatPolicyResolver
 */
public enum VatMode {

    /** 부가세 없음(면세). {@code 최종 = 소계}, 세액 0. <b>A-1 확정 전 임시 기본값</b> */
    NONE,

    /** 표시가에 포함. {@code 세액 = 소계 × rate/(100+rate)}, {@code 최종 = 소계} */
    INCLUDED,

    /** 별도 부과. {@code 세액 = 소계 × rate/100}, {@code 최종 = 소계 + 세액} */
    EXCLUDED;

    /** A-1 확정 전까지 사용하는 임시 정책. */
    public static final VatMode TEMPORARY_DEFAULT = NONE;

    /** 세액 계산 정밀도. 최종 반올림은 {@link RoundingPolicy} 가 한 번만 적용한다. */
    private static final int CALC_SCALE = Money.SCALE;

    /**
     * 소계에서 부가세액을 산출한다.
     *
     * @param ratePercent 부가세율(%). 10 이면 10%
     */
    public Money vatOf(Money subtotal, BigDecimal ratePercent) {
        return switch (this) {
            case NONE -> Money.ZERO;
            case EXCLUDED -> Money.of(subtotal.value()
                    .multiply(ratePercent)
                    .divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP));
            case INCLUDED -> Money.of(subtotal.value()
                    .multiply(ratePercent)
                    .divide(BigDecimal.valueOf(100).add(ratePercent), CALC_SCALE, RoundingMode.HALF_UP));
        };
    }

    /**
     * 세액을 반영한 합계.
     *
     * <p>{@code EXCLUDED} 일 때만 금액이 커진다. 나머지는 소계가 곧 합계다 —
     * 세액은 그 안에 얼마가 들어 있는지를 알려주는 값일 뿐이다.
     */
    public Money totalOf(Money subtotal, Money vat) {
        return this == EXCLUDED ? subtotal.plus(vat) : subtotal;
    }

    /**
     * 설정 문자열 → 정책. 알 수 없는 값('TBD', 오타, 공백)이면 {@link Optional#empty()}.
     *
     * <p>여기서 기본값을 끼워 넣지 않는 이유는 {@link RoundingPolicy#parse} 와 같다 —
     * "설정이 없다"는 사실을 호출자가 알고 WARN 을 남길 수 있어야 한다.
     */
    public static Optional<VatMode> parse(String settingValue) {
        if (settingValue == null || settingValue.isBlank()) {
            return Optional.empty();
        }
        String normalized = settingValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(m -> m.name().equals(normalized))
                .findFirst();
    }
}
