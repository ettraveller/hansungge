package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 자체 가입 마지막 단계 — 비밀번호와 선택 이메일.
 *
 * <p>이름·생년월일·휴대폰은 <b>받지 않는다.</b> 본인확인 값을 그대로 쓴다 — 직접 입력하게 두면
 * 매입 대금을 보낼 때 예금주와 어긋난다.
 */
@Schema(name = "SignUpCompleteRequest", description = "자체 가입 완료")
public record SignUpCompleteRequest(
        @Schema(description = "본인확인을 마친 가입 진행 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "가입 진행 토큰이 없습니다.")
        String signUpToken,

        /**
         * 정책은 관리자 계정과 같다 — 상한 72자는 취향이 아니라 <b>BCrypt 의 한계</b>다.
         * BCrypt 는 72바이트를 넘는 입력을 조용히 잘라낸다.
         */
        @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = UserPasswordPolicy.PATTERN, message = UserPasswordPolicy.MESSAGE)
        String password,

        @Schema(description = "선택. 세금계산서·거래명세서를 받을 주소")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255)
        String email,

        @Schema(description = "이메일을 넣었으면 메일로 받은 6자리")
        @Pattern(regexp = "^$|^\\d{6}$", message = "인증번호는 6자리 숫자입니다.")
        String emailCode
) {}
