package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 타인 비밀번호 초기화 요청 (SUPER_ADMIN 전용).
 *
 * <p>현재 비밀번호를 묻지 않는다 — 애초에 모르기 때문에 초기화하는 것이다.
 * 그만큼 위험한 조작이라 SUPER_ADMIN 으로 제한하고 사유를 필수로 받는다.
 */
@Schema(name = "AdminPasswordResetRequest", description = "관리자 비밀번호 초기화 요청")
public record AdminPasswordResetRequest(

        @Schema(description = "새 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = AdminPasswordPolicy.PATTERN, message = AdminPasswordPolicy.MESSAGE)
        String newPassword,

        @Schema(description = "초기화 사유", example = "본인 확인 후 초기화 요청 접수",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "초기화 사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {
}
