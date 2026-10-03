package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 — 이메일 확정.
 *
 * <p>주소를 다시 받는다. 인증번호는 주소에 묶여 발급되므로, 서버가 "어느 주소의 번호인지"를
 * 알아야 대조할 수 있다. 화면이 보낸 주소와 번호가 맞는 짝일 때만 통과한다.
 */
@Schema(name = "EmailChangeRequest", description = "이메일 등록·변경 요청")
public record EmailChangeRequest(
        @Schema(description = "인증번호를 받은 주소", example = "buyer@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255)
        String email,

        @Schema(description = "메일로 받은 인증번호", example = "123456",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Size(max = 10)
        String code
) {}
