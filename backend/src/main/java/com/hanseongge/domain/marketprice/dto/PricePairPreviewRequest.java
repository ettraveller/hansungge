package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 고시 미리보기 요청. 사유는 받지 않는다 — 아무것도 쓰지 않는다. */
@Schema(name = "PricePairPreviewRequest", description = "고시 미리보기")
public record PricePairPreviewRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "살 때 시세. KRW/1g", example = "220800", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "살 때 시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "살 때 시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "살 때 시세 형식이 올바르지 않습니다.")
        BigDecimal sellPricePerGram,

        @Schema(description = "팔 때 시세. KRW/1g", example = "188000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "팔 때 시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "팔 때 시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "팔 때 시세 형식이 올바르지 않습니다.")
        BigDecimal buyPricePerGram
) {
}
