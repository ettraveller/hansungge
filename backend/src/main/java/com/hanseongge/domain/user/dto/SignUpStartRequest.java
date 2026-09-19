package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 가입 1단계 — 약관 동의.
 *
 * <p>본인확인(개인정보 수집)보다 <b>먼저</b> 받는다. 수집 전에 동의가 있어야 한다.
 *
 * @param signUpToken 소셜 콜백에서 받은 토큰. 휴대폰으로 직접 가입하면 비운다
 */
@Schema(name = "SignUpStartRequest", description = "약관 동의로 가입 시작")
public record SignUpStartRequest(
        @Schema(description = "소셜 콜백에서 받은 가입 진행 토큰. 자체 가입이면 비운다")
        String signUpToken,

        @Schema(description = "약관별 동의 여부. 필수 약관이 하나라도 빠지면 400",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "약관 동의 내역이 필요합니다.")
        @Valid
        List<AgreementChoice> agreements
) {}
