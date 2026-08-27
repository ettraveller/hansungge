package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import com.hanseongge.domain.marketprice.service.PriceBreakdown;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 관리자 "오늘의 시세" 한 줄 (설계서 §8 — 금속×매입/판매별: 원본·출처·수집시각·보정방식·최종가).
 *
 * <p><b>세 값이 모두 들어간다.</b> 최종가만 보여주면 관리자가 "지금 얼마를 보정하고 있는지"를
 * 알 수 없고, 그 상태에서 보정을 또 걸면 이중 보정이 된다.
 */
@Schema(name = "CurrentPriceResponse", description = "현재 최종 시세 (관리자)")
public record CurrentPriceResponse(

        @Schema(description = "금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "금속 표시명", example = "순금 24K")
        String metalName,

        @Schema(description = "매입/판매", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "판매 사용 가능 여부. false 면 '시세문의'", example = "true")
        boolean available,

        @Schema(description = "사용 불가 사유 (AVAILABLE / NONE / STALE)", example = "AVAILABLE")
        CurrentPriceView.Availability availability,

        @Schema(description = "원본 시세 (원/g). 원본을 되짚지 못하면 null", example = "200000.00")
        BigDecimal rawPricePerGram,

        @Schema(description = "보정액 (최종 - 원본)", example = "10000.00")
        BigDecimal adjustment,

        @Schema(description = "최종 적용가 (원/g)", example = "210000.00")
        BigDecimal pricePerGram,

        @Schema(description = "적용된 보정 방식", example = "FIXED_ADJ")
        PolicyMode policyMode,

        @Schema(description = "적용 시각")
        OffsetDateTime effectiveFrom,

        @Schema(description = "변경 사유", example = "관리자 수동 고시")
        String changeReason,

        @Schema(description = "적용자", example = "admin")
        String createdBy
) {

    /** 고시된 적 없는 조합 — 가격 자리를 0 이 아니라 null 로 둔다. */
    public static CurrentPriceResponse none(MetalType metalType, PriceSide priceSide) {
        return new CurrentPriceResponse(metalType, metalType.displayName(), priceSide,
                false, CurrentPriceView.Availability.NONE,
                null, null, null, null, null, null, null);
    }

    public static CurrentPriceResponse of(CurrentPriceView view, PriceBreakdown breakdown) {
        if (view.effective() == null) {
            return none(view.metalType(), view.priceSide());
        }
        MarketPriceEffective e = view.effective();
        return new CurrentPriceResponse(
                e.getMetalType(),
                e.getMetalType().displayName(),
                e.getPriceSide(),
                view.isSellable(),
                view.availability(),
                breakdown.rawPricePerGram(),
                breakdown.adjustment(),
                e.getPricePerGram(),
                e.getPolicyMode(),
                e.getEffectiveFrom(),
                e.getChangeReason(),
                e.getCreatedBy());
    }
}
