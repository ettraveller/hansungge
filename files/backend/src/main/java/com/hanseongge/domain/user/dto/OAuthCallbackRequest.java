package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "OAuthCallbackRequest", description = "소셜 로그인 콜백")
public record OAuthCallbackRequest(

        @Schema(description = "제공자가 돌려준 인가코드", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "인가코드가 없습니다.")
        String code,

        @Schema(description = "인가 요청 때 받은 state", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "state 가 없습니다.")
        String state
) {}
