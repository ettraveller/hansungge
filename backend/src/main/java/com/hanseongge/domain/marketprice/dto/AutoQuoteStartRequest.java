package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 자동고시 시작 — 보정값을 함께 받는다.
 *
 * <p>값을 정하지 않은 채 자동을 켤 수 없게 하는 것이 이 DTO 의 목적이다. 보정 0 으로
 * 켜지면 수집값이 그대로 판매가가 되고, 마진 없이 파는 상태는 판 뒤에야 드러난다.
 */
@Schema(name = "AutoQuoteStartRequest", description = "자동고시 시작 (보정값 포함)")
public record AutoQuoteStartRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "계산 방식. AUTO=받아온 값 그대로, FIXED_ADJ=금액, PERCENT_ADJ=비율",
                example = "FIXED_ADJ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "계산 방식을 선택해 주세요.")
        PolicyMode mode,

        @Schema(description = "살 때 보정값. 금액이면 원/1g, 비율이면 %. 음수 가능",
                example = "3000")
        @Digits(integer = 13, fraction = 4, message = "살 때 보정값 형식이 올바르지 않습니다.")
        BigDecimal sellAdj,

        @Schema(description = "팔 때 보정값", example = "-1500")
        @Digits(integer = 13, fraction = 4, message = "팔 때 보정값 형식이 올바르지 않습니다.")
        BigDecimal buyAdj,

        @Schema(description = "시작 사유", example = "오전 마진 정책 반영",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "시작 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason
) {
}
