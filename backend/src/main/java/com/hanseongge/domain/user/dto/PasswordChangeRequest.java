package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 — 비밀번호 설정·변경.
 *
 * <p>소셜로만 가입해 비밀번호가 없는 회원은 {@link #currentPassword} 없이 새로 정한다.
 * 비밀번호가 있으면 현재 비밀번호를 확인한다 — 열린 채 둔 기기에서 남이 바꾸는 것을 막는다.
 */
@Schema(name = "PasswordChangeRequest", description = "비밀번호 설정·변경")
public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호. 비밀번호가 없는 계정이면 비운다")
        @Size(max = 72)
        String currentPassword,

        @Schema(description = "새 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = UserPasswordPolicy.PATTERN, message = UserPasswordPolicy.MESSAGE)
        String newPassword
) {}
