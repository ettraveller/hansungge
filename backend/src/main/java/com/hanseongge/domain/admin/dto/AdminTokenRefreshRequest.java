package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Access 토큰 재발급 요청. */
@Schema(name = "AdminTokenRefreshRequest", description = "관리자 토큰 재발급 요청")
public record AdminTokenRefreshRequest(

        @Schema(description = "로그인 시 받은 Refresh 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "리프레시 토큰이 필요합니다.")
        String refreshToken
) {
}
