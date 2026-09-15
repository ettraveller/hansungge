package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AdminOtpRecoveryCodesResponse", description = "새 복구 코드")
public record AdminOtpRecoveryCodesResponse(

        @Schema(description = "1회용 복구 코드 10개. 옛 코드는 전부 무효가 됐다")
        List<String> recoveryCodes
) {
}
