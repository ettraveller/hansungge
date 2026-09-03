package com.hanseongge.domain.marketprice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 승인/반려 요청.
 *
 * <p>사유가 필수인 이유: 이상 변동을 <b>승인한다</b>는 것은 안전장치를 의도적으로 통과시키는
 * 행위다. 왜 괜찮다고 판단했는지가 남지 않으면 나중에 그 판단을 검증할 수 없다.
 * 승인 사유는 발행되는 시세의 {@code change_reason} 이 된다.
 */
@Schema(name = "PendingDecisionRequest", description = "시세 승인/반려 요청")
public record PendingDecisionRequest(

        @Schema(description = "판단 사유", example = "국제 금값 급등 확인, 적용 승인",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "사유를 입력해 주세요.")
        @Size(max = 280)
        String reason
) {
}
