package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.service.PriceApplyResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 시세 적용 시도의 결과.
 *
 * <p>{@code applied=false} 는 <b>오류가 아니다.</b> 변동률이 임계값을 넘어 승인 대기로
 * 들어갔다는 뜻이고, HTTP 상태는 200 이다. 이때 현재 시세는 직전 값 그대로다.
 * 화면은 이 값을 보고 "승인이 필요합니다"를 띄운다.
 */
@Schema(name = "PriceApplyResponse", description = "시세 적용 결과")
public record PriceApplyResponse(

        @Schema(description = "실제로 발행되었는가. false 면 승인 대기", example = "true")
        boolean applied,

        @Schema(description = "발행된 최종 시세 ID. 보류면 null", example = "101")
        Long effectiveId,

        @Schema(description = "승인 대기 ID. 발행되었으면 null", example = "7")
        Long pendingId,

        @Schema(description = "원본 시세 (원/g)", example = "200000.00")
        BigDecimal rawPricePerGram,

        @Schema(description = "보정액", example = "10000.00")
        BigDecimal adjustment,

        @Schema(description = "계산된 최종가 (원/g). 보류면 '적용 대기 중인 값'이다", example = "210000.00")
        BigDecimal pricePerGram,

        @Schema(description = "직전 대비 변동률(%). 최초 발행이면 null", example = "5.0000")
        BigDecimal changePercent,

        @Schema(description = "적용 시각. 보류면 null")
        OffsetDateTime effectiveFrom,

        @Schema(description = "사람이 읽을 결과 설명")
        String message
) {

    public static PriceApplyResponse from(PriceApplyResult result) {
        return new PriceApplyResponse(
                result.applied(),
                result.effective() == null ? null : result.effective().getId(),
                result.pending() == null ? null : result.pending().getId(),
                result.calculation().raw().value(),
                result.calculation().adjustment(),
                result.calculation().result().value(),
                result.changePercent(),
                result.effective() == null ? null : result.effective().getEffectiveFrom(),
                result.applied()
                        ? "최종 시세가 적용되었습니다."
                        : "변동률이 허용치를 초과하여 승인 대기로 등록되었습니다. 현재 시세는 변경되지 않았습니다.");
    }
}
