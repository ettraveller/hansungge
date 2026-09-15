package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.QuoteLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 고시로 바뀌는 한 줄. 미리보기와 발행 결과가 같은 모양이다. */
@Schema(name = "QuoteLineResponse", description = "고시로 바뀌는 한 줄")
public record QuoteLineResponse(

        @Schema(description = "금속", example = "GOLD_18K")
        MetalType metalType,

        @Schema(description = "금속 표시명", example = "18K")
        String metalName,

        @Schema(description = "살 때(SELL) / 팔 때(BUY)", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "DIRECT / DERIVED", example = "DERIVED")
        PolicyMode mode,

        @Schema(description = "환산 비율. 직접 입력이면 null", example = "0.7350")
        BigDecimal deriveRatio,

        @Schema(description = "환산에 쓴 24K 값. 직접 입력이면 null", example = "220800.00")
        BigDecimal basePricePerGram,

        @Schema(description = "고시 전 값 (원/g). 처음 고시면 null", example = "160000.00")
        BigDecimal currentPricePerGram,

        @Schema(description = "새 값 (원/g)", example = "162288.00")
        BigDecimal newPricePerGram,

        @Schema(description = "고시 전 대비 변동률(%). 처음 고시면 null", example = "1.43")
        BigDecimal changePercent,

        @Schema(description = "확인 기준을 넘는가", example = "false")
        boolean exceedsLimit
) {

    public static QuoteLineResponse from(QuoteLine line) {
        return new QuoteLineResponse(
                line.metalType(),
                line.metalType().displayName(),
                line.priceSide(),
                line.mode(),
                line.deriveRatio(),
                line.basePricePerGram(),
                line.currentPricePerGram(),
                line.newPrice().value(),
                line.changePercent(),
                line.exceedsLimit());
    }
}
