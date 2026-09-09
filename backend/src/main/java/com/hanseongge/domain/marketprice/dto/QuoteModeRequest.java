package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 자동고시 / 수동고시 전환 요청.
 *
 * <p>보정값을 함께 받지 않는다. 스위치만 내리는 조작이라서다 — 사고 대응 중에
 * 보정값까지 실어 보내게 하면, 화면에 남아 있던 옛 값이 함께 저장되는 사고가 난다.
 *
 * <p>{@code priceSide} 도 받지 않는다. 살 때만 자동이고 팔 때는 수동인 상태를 만들
 * 수 없어야 하기 때문이다 — 금값이 움직이면 두 값이 같이 움직여야 한다.
 */
@Schema(name = "QuoteModeRequest", description = "자동/수동 고시 전환")
public record QuoteModeRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "true = 자동고시, false = 수동고시",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "고시 방식을 선택해 주세요.")
        Boolean autoQuote,

        @Schema(description = "변경 사유", example = "피드 이상으로 수동 전환",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "변경 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason
) {
}
