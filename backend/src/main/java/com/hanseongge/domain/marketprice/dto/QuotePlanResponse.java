package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.service.QuotePlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 고시 미리보기.
 *
 * <p>{@code needsConfirm} 이 true 면 화면이 확인창을 띄우고, 확인받은 뒤에
 * {@code confirmLargeChange: true} 로 다시 보낸다.
 */
@Schema(name = "QuotePlanResponse", description = "고시 미리보기")
public record QuotePlanResponse(

        @Schema(description = "확인 기준을 넘는 줄이 있는가", example = "true")
        boolean needsConfirm,

        @Schema(description = "확인 기준(%)", example = "5")
        BigDecimal limitPercent,

        @Schema(description = "함께 바뀌는 줄. 24K 면 환산 금속까지 들어 있다")
        List<QuoteLineResponse> lines
) {

    public static QuotePlanResponse from(QuotePlan plan) {
        return new QuotePlanResponse(
                plan.needsConfirm(),
                plan.limitPercent(),
                plan.lines().stream().map(QuoteLineResponse::from).toList());
    }
}
