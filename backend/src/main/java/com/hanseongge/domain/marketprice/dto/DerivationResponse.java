package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 18K·14K 한 금속의 고시 방식. */
@Schema(name = "DerivationResponse", description = "18K·14K 고시 방식")
public record DerivationResponse(

        @Schema(description = "금속", example = "GOLD_18K")
        MetalType metalType,

        @Schema(description = "금속 표시명", example = "18K")
        String metalName,

        @Schema(description = "DIRECT / DERIVED", example = "DERIVED")
        PolicyMode mode,

        @Schema(description = "방식 표시명", example = "24K 환산")
        String modeName,

        @Schema(description = "환산 비율. 직접 입력이어도 직전 비율이 남아 있을 수 있다", example = "0.7350")
        BigDecimal ratio,

        @Schema(description = "마지막 변경자", example = "admin")
        String updatedBy,

        @Schema(description = "마지막 변경 시각")
        OffsetDateTime updatedAt
) {

    public static DerivationResponse from(MarketPricePolicy policy) {
        return new DerivationResponse(
                policy.getMetalType(),
                policy.getMetalType().displayName(),
                policy.getMode(),
                policy.getMode().displayName(),
                policy.getDeriveRatio(),
                policy.getUpdatedBy(),
                policy.getUpdatedAt());
    }
}
