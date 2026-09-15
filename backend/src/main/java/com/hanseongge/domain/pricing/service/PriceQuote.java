package com.hanseongge.domain.pricing.service;

import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.global.util.RoundingPolicy;
import com.hanseongge.global.util.VatMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 가격 산출 결과 (개발계획 §13.4).
 *
 * <p>최종가 하나만 돌려주지 않는다. <b>어떻게 그 금액이 나왔는지</b>가 함께 나와야
 * 상세페이지의 내역 표시, 주문 Snapshot 복사(STEP 13), 사후 재현이 모두 가능하다.
 *
 * <p>{@link PriceStatus#INQUIRY_ONLY} 이면 금액 필드가 전부 {@code null} 이다 —
 * <b>0 이 아니다.</b> 0 은 화면에서 "0원"으로 그려질 수 있고, 그것이 이 시스템이 가장
 * 피해야 할 표시다.
 *
 * @param appliedMarketPrice 적용된 최종 시세(원/g). 시세 미연동 상품이면 null
 * @param effectivePriceId   근거가 된 {@code market_prices_effective.id}. 주문 Snapshot 이 보관한다
 * @param subtotal           구성요소 합계 (= 모든 {@link ComponentLine#amount} 의 합)
 * @param vat                부가세액. {@code NONE} 이면 0
 * @param finalUnitPrice     반올림까지 마친 <b>1개 단가</b>
 */
public record PriceQuote(
        PriceStatus status,
        Long productId,
        Long optionId,

        PricePerGram appliedMarketPrice,
        Long effectivePriceId,
        OffsetDateTime priceQuotedAt,

        List<ComponentLine> components,
        Money subtotal,
        VatMode vatMode,
        boolean vatPolicyTemporary,
        Money vat,
        RoundingPolicy roundingPolicy,
        boolean roundingPolicyTemporary,
        Money finalUnitPrice,

        String unavailableReason
) {

    /** 가격을 산출할 수 없는 상태. 금액 자리는 전부 null 이다. */
    public static PriceQuote unavailable(PriceStatus status, Long productId, Long optionId, String reason) {
        return new PriceQuote(status, productId, optionId,
                null, null, null,
                List.of(), null, null, false, null, null, false, null,
                reason);
    }

    /** 고객 상세페이지에 노출할 내역만. */
    public List<ComponentLine> visibleComponents() {
        return components.stream().filter(ComponentLine::visible).toList();
    }

    /**
     * 반올림으로 조정된 금액. 감사·검증용이다.
     *
     * <p>{@code 최종 - (소계 + 세액)}. 백원 단위 올림이면 0 이상이 된다.
     */
    public Money roundingDelta() {
        if (finalUnitPrice == null) {
            return null;
        }
        return finalUnitPrice.minus(vatMode.totalOf(subtotal, vat));
    }

    /** 수량을 곱한 금액. 반올림은 <b>단가에서 이미 끝났으므로</b> 여기서 다시 하지 않는다. */
    public Money totalFor(int quantity) {
        return finalUnitPrice == null ? null : finalUnitPrice.multiply(quantity);
    }
}
