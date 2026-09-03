package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPricePending;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 이상 변동 승인 대기 1건. */
@Schema(name = "PendingResponse", description = "시세 승인 대기")
public record PendingResponse(

        @Schema(description = "승인 대기 ID", example = "7")
        Long id,

        @Schema(description = "근거 원본 ID", example = "55")
        Long rawId,

        @Schema(description = "금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "매입/판매", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "승인 시 적용될 값 (원/g)", example = "260000.00")
        BigDecimal calcPrice,

        @Schema(description = "보류 시점의 현재 시세 (원/g). 지금도 이 값이 적용 중이다",
                example = "210000.00")
        BigDecimal prevPrice,

        @Schema(description = "변동률(%)", example = "23.8095")
        BigDecimal changePercent,

        @Schema(description = "상태", example = "PENDING")
        PendingStatus status,

        @Schema(description = "처리자", example = "admin")
        String decidedBy,

        @Schema(description = "처리 시각")
        OffsetDateTime decidedAt,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt
) {

    public static PendingResponse from(MarketPricePending p) {
        return new PendingResponse(
                p.getId(),
                p.getRawId(),
                p.getMetalType(),
                p.getPriceSide(),
                p.getCalcPrice(),
                p.getPrevPrice(),
                p.getChangePercent(),
                p.getStatus(),
                p.getDecidedBy(),
                p.getDecidedAt(),
                p.getCreatedAt());
    }
}
