package com.hanseongge.domain.admin.dto;

import com.hanseongge.domain.admin.service.AdminOtpService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "AdminOtpStatusResponse", description = "내 OTP 상태")
public record AdminOtpStatusResponse(

        @Schema(description = "모든 관리자에게 OTP 가 필수인가")
        boolean required,

        @Schema(description = "등록을 마쳤는가")
        boolean enabled,

        @Schema(description = "등록 완료 시각")
        OffsetDateTime enabledAt,

        @Schema(description = "아직 쓰지 않은 복구 코드 수", example = "10")
        long recoveryCodesRemaining
) {

    public static AdminOtpStatusResponse from(AdminOtpService.Status status) {
        return new AdminOtpStatusResponse(status.required(), status.enabled(), status.enabledAt(),
                status.recoveryCodesRemaining());
    }
}
