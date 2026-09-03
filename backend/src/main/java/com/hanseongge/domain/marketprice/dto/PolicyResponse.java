package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 보정 정책 1건. */
@Schema(name = "PolicyResponse", description = "시세 보정 정책")
public record PolicyResponse(

        @Schema(description = "정책 ID", example = "1")
        Long id,

        @Schema(description = "금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "매입/판매", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "보정 방식", example = "FIXED_ADJ")
        PolicyMode mode,

        @Schema(description = "보정 방식 표시명", example = "정액 보정")
        String modeName,

        @Schema(description = "FIXED_ADJ 정액 보정값", example = "10000.00")
        BigDecimal adjAmount,

        @Schema(description = "PERCENT_ADJ 정률 보정값(%)", example = "-7.0000")
        BigDecimal adjPercent,

        @Schema(description = "MANUAL 지정가", example = "220000.00")
        BigDecimal manualPrice,

        @Schema(description = "마지막 변경자", example = "admin")
        String updatedBy,

        @Schema(description = "마지막 변경 시각")
        OffsetDateTime updatedAt
) {

    public static PolicyResponse from(MarketPricePolicy policy) {
        return new PolicyResponse(
                policy.getId(),
                policy.getMetalType(),
                policy.getPriceSide(),
                policy.getMode(),
                policy.getMode().displayName(),
                policy.getAdjAmount(),
                policy.getAdjPercent(),
                policy.getManualPrice(),
                policy.getUpdatedBy(),
                policy.getUpdatedAt());
    }
}
