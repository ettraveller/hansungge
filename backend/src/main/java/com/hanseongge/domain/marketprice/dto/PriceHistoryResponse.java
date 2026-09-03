package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 시세 변경 이력 1건.
 *
 * <p>{@code market_prices_effective} 가 append-only 라서 <b>별도 이력 테이블이 없다.</b>
 * 마감된 행({@code effectiveTo != null})이 곧 과거 이력이고, 마감되지 않은 행이 현재값이다.
 */
@Schema(name = "PriceHistoryResponse", description = "시세 변경 이력")
public record PriceHistoryResponse(

        @Schema(description = "최종 시세 ID", example = "101")
        Long id,

        @Schema(description = "금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "매입/판매", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "최종 적용가 (원/g)", example = "210000.00")
        BigDecimal pricePerGram,

        @Schema(description = "근거 원본 ID", example = "55")
        Long baseRawId,

        @Schema(description = "적용된 보정 방식", example = "FIXED_ADJ")
        PolicyMode policyMode,

        @Schema(description = "적용 시작")
        OffsetDateTime effectiveFrom,

        @Schema(description = "적용 종료. null 이면 현재값")
        OffsetDateTime effectiveTo,

        @Schema(description = "현재 적용 중인가", example = "true")
        boolean current,

        @Schema(description = "변경 사유", example = "관리자 수동 고시")
        String changeReason,

        @Schema(description = "적용자", example = "admin")
        String createdBy
) {

    public static PriceHistoryResponse from(MarketPriceEffective e) {
        return new PriceHistoryResponse(
                e.getId(),
                e.getMetalType(),
                e.getPriceSide(),
                e.getPricePerGram(),
                e.getBaseRawId(),
                e.getPolicyMode(),
                e.getEffectiveFrom(),
                e.getEffectiveTo(),
                e.isCurrent(),
                e.getChangeReason(),
                e.getCreatedBy());
    }
}
