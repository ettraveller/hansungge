package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 환산 비율 미리보기 요청. 비율의 범위·자릿수는 서비스가 한 곳에서 검사한다. */
@Schema(name = "DerivationPreviewRequest", description = "24K 환산 미리보기")
public record DerivationPreviewRequest(

        @Schema(description = "환산할 금속 (GOLD_18K / GOLD_14K)", example = "GOLD_18K",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "환산 비율. 0 초과 1 이하, 소수 넷째 자리까지", example = "0.735",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "환산 비율을 입력해 주세요.")
        BigDecimal ratio
) {
}
