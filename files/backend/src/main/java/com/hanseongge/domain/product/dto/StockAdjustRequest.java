package com.hanseongge.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 재고 실사 반영 요청.
 *
 * <p><b>증감이 아니라 절대 수량</b>을 받는다. 실사는 "세어 보니 7개였다"이지
 * "3개 줄여라"가 아니다. 증감으로 받으면 요청이 두 번 도착했을 때 재고가 두 번 깎인다.
 */
@Schema(name = "StockAdjustRequest", description = "재고 조정 요청")
public record StockAdjustRequest(

        @Schema(description = "조정 후 재고 수량 (절대값)", example = "7",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "재고 수량을 입력해 주세요.")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer stock,

        @Schema(description = "조정 사유", example = "2026-08-26 실사 반영",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "조정 사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {
}
