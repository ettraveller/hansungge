package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "IdentityCompleteRequest", description = "본인확인 결과 제출")
public record IdentityCompleteRequest(
        @Schema(description = "본인확인 시작 때 받은 진행 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "진행 토큰이 없습니다.")
        String token,

        @Schema(description = "본인확인을 마치고 돌아온 화면 주소의 providerToken",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "본인확인 결과가 없습니다.")
        String providerToken
) {}
