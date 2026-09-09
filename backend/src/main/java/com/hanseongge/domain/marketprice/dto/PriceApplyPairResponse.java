package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.service.PairedApplyResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 살 때·팔 때 동시 고시 결과.
 *
 * <p>{@code applied} 는 둘 다 발행됐을 때만 참이다. 한쪽만 나가는 상태는 이 경로에서
 * 만들지 않는다.
 */
@Schema(name = "PriceApplyPairResponse", description = "살 때·팔 때 동시 고시 결과")
public record PriceApplyPairResponse(

        @Schema(description = "둘 다 발행되었는가", example = "true")
        boolean applied,

        @Schema(description = "살 때 결과")
        PriceApplyResponse sell,

        @Schema(description = "팔 때 결과")
        PriceApplyResponse buy
) {

    public static PriceApplyPairResponse from(PairedApplyResult result) {
        return new PriceApplyPairResponse(
                result.applied(),
                PriceApplyResponse.from(result.sell()),
                PriceApplyResponse.from(result.buy()));
    }
}
