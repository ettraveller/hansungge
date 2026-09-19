package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "SmsLoginRequest", description = "인증번호 로그인")
public record SmsLoginRequest(
        @Schema(description = "가입한 휴대폰번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "휴대폰번호를 입력해 주세요.")
        @Size(max = 20)
        String phone,

        @Schema(description = "문자로 받은 6자리", example = "482913", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다.")
        String code
) {}
