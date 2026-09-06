package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(name = "IdentityCompleteRequest", description = "본인확인 결과 제출")
public record IdentityCompleteRequest(
        @Schema(description = "가입 진행 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "가입 진행 토큰이 없습니다.")
        String signUpToken,

        @Schema(description = "본인확인 공급자가 돌려준 조회용 토큰",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "본인확인 결과가 없습니다.")
        String providerToken,

        /**
         * 자체 가입에서만 보낸다.
         *
         * <p>여기서 함께 받는 이유 — 이미 가입한 회원(같은 DI)으로 밝혀지면 <b>이 단계에서
         * 바로</b> 기존 계정에 비밀번호를 붙이고 로그인시킨다. 약관 화면까지 갔다가
         * "이미 회원이시네요"라고 되돌리지 않는다.
         */
        @Schema(description = "비밀번호. 자체 가입에만 필요하다")
        @Pattern(regexp = "^$|" + UserPasswordPolicy.PATTERN, message = UserPasswordPolicy.MESSAGE)
        String password
) {}
