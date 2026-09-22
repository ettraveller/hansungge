package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 약관 동의 — 자체 가입의 시작, 또는 소셜 가입의 전부.
 *
 * <p>자체 가입은 간편인증(개인정보 수집)보다 <b>먼저</b> 받는다. 수집 전에 동의가 있어야 한다.
 *
 * @param signUpToken 소셜 가입일 때 콜백에서 받은 토큰. 자체 가입이면 비운다
 */
@Schema(name = "SignUpStartRequest", description = "약관 동의")
public record SignUpStartRequest(
        @Schema(description = "소셜 콜백에서 받은 가입 진행 토큰. 소셜 가입(/signup/social)에서만 쓴다")
        String signUpToken,

        @Schema(description = "약관별 동의 여부. 필수 약관이 하나라도 빠지면 400",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "약관 동의 내역이 필요합니다.")
        @Valid
        List<AgreementChoice> agreements
) {}
