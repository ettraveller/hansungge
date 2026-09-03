package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 본인 비밀번호 변경 요청.
 *
 * <p>현재 비밀번호를 함께 받는 이유: 자리를 비운 사이 열린 화면으로 비밀번호가 바뀌면
 * 계정을 통째로 빼앗긴다. 토큰만으로 비밀번호를 바꿀 수 있으면 토큰 탈취가 곧 계정 탈취다.
 */
@Schema(name = "AdminPasswordChangeRequest", description = "본인 비밀번호 변경 요청")
public record AdminPasswordChangeRequest(

        @Schema(description = "현재 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        @Size(max = 72)
        String currentPassword,

        @Schema(description = "새 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = AdminPasswordPolicy.PATTERN, message = AdminPasswordPolicy.MESSAGE)
        String newPassword
) {
}
