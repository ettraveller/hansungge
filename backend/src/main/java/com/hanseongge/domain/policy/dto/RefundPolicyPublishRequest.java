package com.hanseongge.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RefundPolicyPublishRequest", description = "환불규정 새 판 발행")
public record RefundPolicyPublishRequest(

        @Schema(description = "규정 전문. 저장하면 쇼핑몰과 주문서에 바로 보인다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "규정 내용을 입력해 주세요.")
        @Size(max = 20_000, message = "규정은 20,000자를 넘을 수 없습니다.")
        String content,

        @Schema(description = "개정 사유", example = "반품 검수 기간을 7일로 명시",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "개정 사유를 입력해 주세요.")
        @Size(max = 200)
        String changeNote
) {
}
