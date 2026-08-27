package com.hanseongge.domain.pricing.service;

/**
 * 구성요소 계산 방식 (개발계획 §13.2).
 *
 * <p>V3 의 {@code SIMPLE} 모델에서 실제로 쓰이는 것은 {@link #PER_GRAM_MARKET},
 * {@link #FIXED_AMOUNT}, {@link #MULTIPLIER_OF_BASE} 셋뿐이다. 나머지는
 * {@code product_price_components}(결함 D-1) 가 들어올 때를 위해 정의만 해둔다 —
 * 계산 코드를 나중에 갈아엎지 않기 위해서다.
 */
public enum CalcMethod {

    /** {@code 최종시세(metal, side) × weight_g} */
    PER_GRAM_MARKET,
    /** {@code unit_amount × weight_g} — g당 고정 세공비 */
    PER_GRAM_FIXED,
    /** {@code unit_amount} — 부자재·포장·감정서 */
    FIXED_AMOUNT,
    /** {@code METAL_BASE 합계 × rate/100} */
    PERCENT_OF_BASE,
    /** {@code 직전까지 누적 × rate/100} */
    PERCENT_OF_SUBTOTAL,
    /** {@code METAL_BASE 합계 × rate} — MARKET_MULTIPLIER 대응 */
    MULTIPLIER_OF_BASE
}
