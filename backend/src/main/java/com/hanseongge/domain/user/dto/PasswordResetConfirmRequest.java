package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordResetConfirmRequest", description = "비밀번호 재설정")
public record PasswordResetConfirmRequest(
        @Schema(description = "가입한 이메일", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255)
        String email,

        @Schema(description = "메일로 받은 6자리 숫자", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "인증번호를 입력해 주세요.")
        String code,

        @Schema(description = "새 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = UserPasswordPolicy.PATTERN, message = UserPasswordPolicy.MESSAGE)
        String newPassword
) {}
