package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 새 비밀번호 설정.
 *
 * <p>임시 비밀번호를 문자로 보내지 않는다. 문자는 단말에 평문으로 남고, 잠금화면 미리보기로
 * 드러나며, 서버가 평문을 알아야 해서 로그로 샐 수 있다. 본인확인이 이미 끝났으므로 그 자리에서
 * 새 비밀번호를 정한다.
 */
@Schema(name = "PasswordResetRequest", description = "비밀번호 재설정")
public record PasswordResetRequest(
        @Schema(description = "본인확인 뒤 받은 재설정 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "재설정 토큰이 없습니다.")
        String resetToken,

        @Schema(description = "새 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = UserPasswordPolicy.PATTERN, message = UserPasswordPolicy.MESSAGE)
        String newPassword
) {}
