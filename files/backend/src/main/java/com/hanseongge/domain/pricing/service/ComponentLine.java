package com.hanseongge.domain.pricing.service;

import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.global.util.Weight;

import java.math.BigDecimal;

/**
 * 가격 내역 한 줄.
 *
 * <p><b>불변식: 모든 줄의 {@link #amount} 합계 = 소계.</b> 이것이 깨지면 주문 명세서의
 * 항목 합이 청구액과 달라진다. 그래서 각 줄을 {@link Money}(전 단위, scale 2)로 확정한 뒤
 * 더한다 — 줄마다 확정하고 더하면 합이 정확히 맞지만, 실수로 누적하다가 마지막에
 * 한 번 자르면 줄 합계와 소계가 어긋난다.
 *
 * <p>계산 근거({@link #unitPricePerGram}, {@link #weight}, {@link #rate})를 함께 담는 이유:
 * 상세페이지에 "210,000원/g × 3.75g" 로 보여주려면 결과 금액만으로는 부족하고,
 * 주문 Snapshot(STEP 13)도 이 근거를 그대로 복사해야 나중에 재현할 수 있다.
 *
 * @param unitPricePerGram PER_GRAM_* 방식일 때의 단가. 아니면 null
 * @param weight           PER_GRAM_* 방식일 때의 중량. 아니면 null
 * @param rate             PERCENT_* / MULTIPLIER_* 방식일 때의 비율. 아니면 null
 * @param visible          고객 상세페이지에 이 줄을 노출할지
 */
public record ComponentLine(
        int sort,
        ComponentType type,
        String label,
        CalcMethod method,
        Money amount,
        PricePerGram unitPricePerGram,
        Weight weight,
        BigDecimal rate,
        boolean visible
) {

    /** 시세 × 중량 (METAL_BASE). */
    public static ComponentLine perGramMarket(int sort, String label, PricePerGram unitPrice, Weight weight) {
        return new ComponentLine(sort, ComponentType.METAL_BASE, label, CalcMethod.PER_GRAM_MARKET,
                Money.fromGram(unitPrice, weight), unitPrice, weight, null, true);
    }

    /** 정액 (LABOR, MATERIAL, PACKAGING …). */
    public static ComponentLine fixedAmount(int sort, ComponentType type, String label, Money amount) {
        return new ComponentLine(sort, type, label, CalcMethod.FIXED_AMOUNT,
                amount, null, null, null, true);
    }

    /** 금액 기준 배수 가산 (MARKET_MULTIPLIER 대응). */
    public static ComponentLine multiplierOfBase(int sort, String label, Money base, BigDecimal extraRate) {
        return new ComponentLine(sort, ComponentType.MARGIN, label, CalcMethod.MULTIPLIER_OF_BASE,
                base.multiply(extraRate), null, null, extraRate, true);
    }
}
