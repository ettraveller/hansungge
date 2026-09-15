package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 타인 OTP 초기화 요청 (SUPER_ADMIN 전용). 휴대폰 분실·교체 때 쓴다.
 */
@Schema(name = "AdminOtpResetRequest", description = "관리자 OTP 초기화 요청")
public record AdminOtpResetRequest(

        @Schema(description = "초기화 사유", example = "휴대폰 분실 — 본인 확인 후 초기화",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "초기화 사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {
}
