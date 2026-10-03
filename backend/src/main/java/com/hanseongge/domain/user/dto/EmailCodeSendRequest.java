package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 마이페이지 — 등록·변경할 이메일로 인증번호 보내기. */
@Schema(name = "EmailCodeSendRequest", description = "이메일 인증번호 발송 요청 (마이페이지)")
public record EmailCodeSendRequest(
        @Schema(description = "인증번호를 받을 주소", example = "buyer@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255)
        String email
) {}
