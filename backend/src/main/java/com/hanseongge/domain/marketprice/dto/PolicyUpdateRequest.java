package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 보정 정책 변경 요청.
 *
 * <p>모드별 필수값(예: MANUAL 의 지정가)은 Bean Validation 으로 표현할 수 없어
 * 서비스가 검사한다 — 한 필드의 필수 여부가 다른 필드 값에 달려 있기 때문이다.
 */
@Schema(name = "PolicyUpdateRequest", description = "시세 보정 정책 변경 요청")
public record PolicyUpdateRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "매입/판매", example = "SELL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "매입/판매를 선택해 주세요.")
        PriceSide priceSide,

        @Schema(description = "보정 방식", example = "FIXED_ADJ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "보정 방식을 선택해 주세요.")
        PolicyMode mode,

        @Schema(description = "FIXED_ADJ 정액 보정값. 음수 가능", example = "10000")
        BigDecimal adjAmount,

        @Schema(description = "PERCENT_ADJ 정률 보정값(%). -7 이면 7% 차감", example = "-7")
        BigDecimal adjPercent,

        @Schema(description = "MANUAL 지정가 (원/g)", example = "220000")
        BigDecimal manualPrice,

        @Schema(description = "변경 사유", example = "판매 마진 정책 변경",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "변경 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason
) {
}
