package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "SmsSendRequest", description = "인증번호 로그인 — 문자 요청")
public record SmsSendRequest(
        @Schema(description = "가입한 휴대폰번호", example = "010-1234-5678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "휴대폰번호를 입력해 주세요.")
        @Size(max = 20)
        String phone
) {}
