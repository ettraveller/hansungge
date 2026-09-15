package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 관리자 "현재 시세" 한 줄.
 *
 * <p>변동폭을 함께 담는다. 고시한 사람이 "방금 얼마나 움직였나"를 표에서 바로 봐야
 * 자릿수 실수를 늦지 않게 알아챈다.
 */
@Schema(name = "CurrentPriceResponse", description = "현재 시세 (관리자)")
public record CurrentPriceResponse(

        @Schema(description = "금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "금속 표시명", example = "순금 24K")
        String metalName,

        @Schema(description = "살 때(SELL) / 팔 때(BUY)", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "판매에 쓸 수 있는가. false 면 아직 고시되지 않아 '시세문의'", example = "true")
        boolean available,

        @Schema(description = "AVAILABLE / NONE", example = "AVAILABLE")
        CurrentPriceView.Availability availability,

        @Schema(description = "고시가 (원/g)", example = "220800.00")
        BigDecimal pricePerGram,

        @Schema(description = "직전 고시 대비 변동폭. 처음 고시면 null")
        PriceChange change,

        @Schema(description = "이 값이 나온 방식. 옛 행이면 V19 이전 값(FIXED_ADJ 등)일 수 있다", example = "DIRECT")
        PolicyMode mode,

        @Schema(description = "고시 시각")
        OffsetDateTime effectiveFrom,

        @Schema(description = "고시 사유", example = "오전 고시")
        String changeReason,

        @Schema(description = "고시한 관리자", example = "admin")
        String createdBy
) {

    /** 고시된 적 없는 조합 — 가격 자리를 0 이 아니라 null 로 둔다. */
    public static CurrentPriceResponse none(MetalType metalType, PriceSide priceSide) {
        return new CurrentPriceResponse(metalType, metalType.displayName(), priceSide,
                false, CurrentPriceView.Availability.NONE,
                null, null, null, null, null, null);
    }

    /** @param previous 같은 금속·쪽의 직전 행. 없으면 null */
    public static CurrentPriceResponse of(CurrentPriceView view, MarketPriceEffective previous) {
        MarketPriceEffective e = view.effective();
        if (e == null) {
            return none(view.metalType(), view.priceSide());
        }
        return new CurrentPriceResponse(
                e.getMetalType(),
                e.getMetalType().displayName(),
                e.getPriceSide(),
                view.isSellable(),
                view.availability(),
                e.getPricePerGram(),
                PriceChange.of(e.getPricePerGram(), previous),
                e.getPolicyMode(),
                e.getEffectiveFrom(),
                e.getChangeReason(),
                e.getCreatedBy());
    }
}
