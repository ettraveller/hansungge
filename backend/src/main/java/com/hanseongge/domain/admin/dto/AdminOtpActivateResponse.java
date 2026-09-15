package com.hanseongge.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * OTP 등록 완료 응답. 복구 코드 평문은 이 응답에서만 나간다 — 서버에는 요약값만 남는다.
 */
@Schema(name = "AdminOtpActivateResponse", description = "OTP 등록 완료")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOtpActivateResponse(

        @Schema(description = "1회용 복구 코드 10개. 다시 볼 수 없다")
        List<String> recoveryCodes,

        @Schema(description = "첫 로그인 도중의 등록이면 발급된 토큰. 로그인한 상태에서 등록했으면 없다")
        AdminLoginResponse login
) {
}
