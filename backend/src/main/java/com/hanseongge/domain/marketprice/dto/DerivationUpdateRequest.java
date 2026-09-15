package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 18K·14K 고시 방식 변경 요청.
 *
 * <p>{@code ratio} 의 필수 여부가 {@code mode} 에 달려 있어 Bean Validation 으로 표현하지
 * 않고 서비스가 검사한다.
 */
@Schema(name = "DerivationUpdateRequest", description = "18K·14K 고시 방식 변경")
public record DerivationUpdateRequest(

        @Schema(description = "금속 (GOLD_18K / GOLD_14K)", example = "GOLD_18K",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "DIRECT(직접 입력) / DERIVED(24K 환산)", example = "DERIVED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "고시 방식을 선택해 주세요.")
        PolicyMode mode,

        @Schema(description = "환산 비율. DERIVED 일 때 필수", example = "0.735")
        BigDecimal ratio,

        @Schema(description = "변경 사유", example = "환산 비율 확정", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "변경 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason,

        @Schema(description = "다시 나갈 값이 확인 기준을 넘는다는 것을 확인했는가", example = "false")
        Boolean confirmLargeChange
) {
}
