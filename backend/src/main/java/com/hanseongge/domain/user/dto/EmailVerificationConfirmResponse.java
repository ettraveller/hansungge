package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이메일 인증 통과.
 *
 * @param signUpToken 다음 단계(본인확인)로 들고 갈 토큰
 */
@Schema(name = "EmailVerificationConfirmResponse", description = "이메일 인증 결과")
public record EmailVerificationConfirmResponse(
        @Schema(description = "가입 진행 토큰")
        String signUpToken
) {}
