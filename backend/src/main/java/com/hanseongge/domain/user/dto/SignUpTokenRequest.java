package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 진행 토큰 하나만 보내는 요청 (소셜 연결 확인). */
@Schema(name = "SignUpTokenRequest", description = "가입 진행 토큰")
public record SignUpTokenRequest(
        @Schema(description = "가입 진행 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "가입 진행 토큰이 없습니다.")
        String signUpToken
) {}
