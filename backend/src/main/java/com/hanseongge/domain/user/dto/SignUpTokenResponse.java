package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 다음 단계로 들고 갈 가입 진행 토큰. */
@Schema(name = "SignUpTokenResponse", description = "가입 진행 토큰")
public record SignUpTokenResponse(
        @Schema(description = "가입 진행 토큰")
        String signUpToken
) {}
