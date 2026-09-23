package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.VerificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 인증번호 확인 — 로그인·비밀번호 찾기가 함께 쓴다.
 *
 * <p>맞았는지 틀렸는지만 알려준다. "만료됐다"와 "틀렸다"를 구분해 주면 유효한 번호를 찾는 데 쓰인다.
 */
@Schema(name = "CodeVerifyRequest", description = "인증번호 확인")
public record CodeVerifyRequest(

        @Schema(description = "SMS 또는 EMAIL", example = "SMS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "받은 방법을 선택해 주세요.")
        VerificationChannel channel,

        @Schema(description = "휴대폰번호 또는 이메일 주소", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "받은 곳을 입력해 주세요.")
        String target,

        @Schema(description = "6자리 인증번호", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자입니다.")
        String code
) {}
