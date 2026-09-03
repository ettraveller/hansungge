package com.hanseongge.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;

/**
 * 최종 판매가 반올림 정책.
 *
 * <p>운영 값은 코드가 아니라 {@code system_settings.PRICE_ROUNDING_MODE} 에서 읽는다.
 * 정책이 바뀌어도 재배포 없이 설정 변경만으로 반영되도록 한다.
 *
 * <p><b>[TODO A-2 — 미확정]</b> 실제 적용 단위·방식은 발주자 확정 대기 중이다.
 * 확정 전까지 {@link #WON_FLOOR}(원단위 절사)로 임시 동작한다.
 *
 * @see PriceRoundingResolver
 */
public enum RoundingPolicy {

    /** 반올림 없음 — 전(錢) 단위까지 그대로 둔다. */
    NONE(1, RoundingMode.HALF_UP, false),

    /** 원단위 절사 — 1원 미만 절사. <b>A-2 확정 전 임시 기본값</b> */
    WON_FLOOR(1, RoundingMode.FLOOR, true),

    /** 십원 단위 절사 */
    FLOOR_10(10, RoundingMode.FLOOR, true),
    /** 백원 단위 절사 */
    FLOOR_100(100, RoundingMode.FLOOR, true),
    /** 천원 단위 절사 */
    FLOOR_1000(1000, RoundingMode.FLOOR, true),

    /** 백원 단위 반올림 */
    HALF_UP_100(100, RoundingMode.HALF_UP, true),
    /** 천원 단위 반올림 */
    HALF_UP_1000(1000, RoundingMode.HALF_UP, true),

    /** 백원 단위 올림 */
    CEIL_100(100, RoundingMode.CEILING, true),
    /** 천원 단위 올림 */
    CEIL_1000(1000, RoundingMode.CEILING, true);

    /** A-2 확정 전까지 사용하는 임시 정책. */
    public static final RoundingPolicy TEMPORARY_DEFAULT = WON_FLOOR;

    private final int unit;
    private final RoundingMode mode;
    private final boolean truncating;

    RoundingPolicy(int unit, RoundingMode mode, boolean truncating) {
        this.unit = unit;
        this.mode = mode;
        this.truncating = truncating;
    }

    BigDecimal apply(BigDecimal amount) {
        if (!truncating) {
            return amount;
        }
        BigDecimal u = BigDecimal.valueOf(unit);
        return amount.divide(u, 0, mode).multiply(u);
    }

    /**
     * 설정 문자열 → 정책.
     *
     * <p>알 수 없는 값('TBD', 오타, 공백)이면 {@link Optional#empty()} 를 돌려준다.
     * 여기서 임의로 기본값을 끼워 넣지 않는 이유는, "설정이 없다"는 사실을 호출자가
     * 알고 WARN 로그를 남길 수 있어야 하기 때문이다.
     */
    public static Optional<RoundingPolicy> parse(String settingValue) {
        if (settingValue == null || settingValue.isBlank()) {
            return Optional.empty();
        }
        String normalized = settingValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(p -> p.name().equals(normalized))
                .findFirst();
    }
}
