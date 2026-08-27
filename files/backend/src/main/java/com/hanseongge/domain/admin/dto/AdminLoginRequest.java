package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 로그인 요청.
 *
 * <p>형식 검증은 최소한만 건다. 아이디 길이·문자종류 규칙을 여기서 엄격하게 검사하면
 * 오류 응답의 모양만으로 "존재할 수 있는 아이디 형식"을 알려주는 셈이 된다.
 */
@Schema(name = "AdminLoginRequest", description = "관리자 로그인 요청")
public record AdminLoginRequest(

        @Schema(description = "관리자 아이디", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(max = 60)
        String loginId,

        @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72)
        String password
) {
}
