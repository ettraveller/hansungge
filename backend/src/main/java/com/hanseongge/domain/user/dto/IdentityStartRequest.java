package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.identity.IdentityMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 본인확인 시작.
 *
 * <p>복귀 주소를 받지 않는다. 본인확인 결과는 공급자가 백엔드 수신 주소로 보내고, 백엔드가
 * 설정된 화면({@code app.identity.return-page-url})으로 넘긴다 — 화면이 주소를 정하게 하면
 * 공개 리다이렉트를 막는 검사가 따로 필요해진다.
 */
@Schema(name = "IdentityStartRequest", description = "본인확인 시작 요청")
public record IdentityStartRequest(
        @Schema(description = "약관 동의를 마친 가입 진행 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "가입 진행 토큰이 없습니다.")
        String signUpToken,

        @Schema(description = "SIMPLE 간편인증(앱) / PHONE 휴대폰 본인확인(문자). 비우면 기본값",
                example = "PHONE")
        IdentityMethod method
) {}
