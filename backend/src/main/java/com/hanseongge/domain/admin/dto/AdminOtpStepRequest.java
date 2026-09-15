package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 로그인 도중의 2단계 표만 싣는 요청. 이미 로그인한 관리자는 본문 없이 부른다.
 */
@Schema(name = "AdminOtpStepRequest", description = "로그인 2단계 표")
public record AdminOtpStepRequest(

        @Schema(description = "로그인 응답의 stepToken. 로그인한 상태면 비운다")
        @Size(max = 2000)
        String stepToken
) {
}
