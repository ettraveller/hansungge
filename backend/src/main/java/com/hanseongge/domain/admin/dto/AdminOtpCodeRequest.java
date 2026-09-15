package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * OTP 코드 입력. 인증 앱의 6자리 또는 복구 코드({@code ABCD-EFGH}).
 */
@Schema(name = "AdminOtpCodeRequest", description = "OTP 코드 입력")
public record AdminOtpCodeRequest(

        @Schema(description = "로그인 응답의 stepToken. 로그인한 상태에서 부르면 비운다")
        @Size(max = 2000)
        String stepToken,

        @Schema(description = "인증 앱의 6자리 또는 복구 코드", example = "123456",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "인증 코드를 입력해 주세요.")
        @Size(max = 20, message = "인증 코드가 너무 깁니다.")
        String code
) {
}
