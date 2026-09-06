package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 + 비밀번호 로그인.
 *
 * <p>비밀번호에 형식 검증을 걸지 않는다. 정책이 바뀌기 전에 가입한 사람이 <b>로그인
 * 단계에서 막히면</b> 들어와서 바꿀 방법조차 없어진다. 검증은 설정할 때만 한다.
 */
@Schema(name = "PasswordSignInRequest", description = "이메일 로그인")
public record PasswordSignInRequest(
        @Schema(description = "가입한 이메일", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Size(max = 255)
        String email,

        @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72)
        String password
) {}
