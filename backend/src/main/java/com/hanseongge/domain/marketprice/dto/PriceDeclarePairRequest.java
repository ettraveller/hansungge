package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 살 때·팔 때를 함께 고시하는 요청.
 *
 * <p>{@code priceSide} 가 없다. 한쪽만 지정할 수 없게 하는 것이 이 DTO 의 목적이다 —
 * 한쪽씩 고시하면 그 사이에 "살 때는 있고 팔 때는 없는" 시세표가 화면에 걸린다.
 */
@Schema(name = "PriceDeclarePairRequest", description = "살 때·팔 때 동시 고시")
public record PriceDeclarePairRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "고객이 살 때(판매) 원본 시세. KRW/1g, 돈 단위가 아니다",
                example = "220800", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "살 때 시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "살 때 시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "살 때 시세 형식이 올바르지 않습니다.")
        BigDecimal sellPricePerGram,

        @Schema(description = "고객이 팔 때(매입) 원본 시세. KRW/1g",
                example = "188000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "팔 때 시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "팔 때 시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "팔 때 시세 형식이 올바르지 않습니다.")
        BigDecimal buyPricePerGram,

        @Schema(description = "고시 사유", example = "오전 고시",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "고시 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason
) {
}
