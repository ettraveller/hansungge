package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "IdentityStartRequest", description = "본인확인 시작 요청")
public record IdentityStartRequest(
        @Schema(description = "이메일 인증 또는 소셜 콜백에서 받은 가입 진행 토큰",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "가입 진행 토큰이 없습니다.")
        String signUpToken,

        /**
         * 인증을 마친 뒤 돌아올 우리 쪽 주소.
         *
         * <p>화면이 정하는 이유 — 자체 가입과 소셜 가입이 서로 다른 화면으로 돌아가야 하고,
         * 로컬·운영의 주소도 다르다. 다만 아무 주소나 받으면 <b>공개 리다이렉트</b>가 되므로
         * 서비스가 허용 목록으로 거른다.
         */
        @Schema(description = "인증 후 돌아올 주소", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "복귀 주소가 없습니다.")
        String returnUrl
) {}
