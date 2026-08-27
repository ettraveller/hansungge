package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.global.util.PricePerGram;

import java.math.BigDecimal;

/**
 * 보정 정책 적용 결과 — <b>세 값이 함께 나온다.</b>
 *
 * <p>최종가만 돌려주면 "원본이 얼마였고 얼마를 보정했는가"가 호출부에서 사라진다.
 * 요구사항이 금지하는 것이 정확히 그 상태(원본과 보정 시세를 하나의 값으로 덮어쓰기)이므로,
 * 계산 결과 타입부터 세 값을 분리해서 들고 다닌다.
 *
 * @param raw        L1 원본가
 * @param result     L3 최종 적용가
 * @param adjustment {@code result - raw}. 부호가 그대로 의미를 갖는다(매입 마진은 음수)
 * @param mode       적용된 정책 모드
 */
public record PriceCalculation(
        PricePerGram raw,
        PricePerGram result,
        BigDecimal adjustment,
        PolicyMode mode
) {

    static PriceCalculation of(PricePerGram raw, PricePerGram result, PolicyMode mode) {
        return new PriceCalculation(raw, result, result.value().subtract(raw.value()), mode);
    }

    /** 보정이 실제로 있었는가. 감사·화면 표시에서 "보정 없음"을 구분하는 데 쓴다. */
    public boolean hasAdjustment() {
        return adjustment.compareTo(BigDecimal.ZERO) != 0;
    }
}
