package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.VerificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 인증번호 보내기 — 로그인·비밀번호 찾기가 함께 쓴다.
 *
 * <p>가입 여부는 응답으로 알려주지 않는다. 회원이 아니면 실제로 보내지 않을 뿐, 응답은 같다.
 */
@Schema(name = "CodeSendRequest", description = "인증번호 발송 요청")
public record CodeSendRequest(

        @Schema(description = "SMS 또는 EMAIL", example = "SMS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "받을 방법을 선택해 주세요.")
        VerificationChannel channel,

        @Schema(description = "휴대폰번호 또는 이메일 주소", example = "010-1234-5678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "받을 곳을 입력해 주세요.")
        String target
) {}
