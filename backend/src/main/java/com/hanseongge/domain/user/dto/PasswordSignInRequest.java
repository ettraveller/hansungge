package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 휴대폰번호 + 비밀번호 로그인.
 *
 * <p>번호 형식은 여기서 막지 않는다. 형식이 틀린 번호도 "없는 계정"과 같은 응답을 받아야
 * 한다 — 형식 오류만 다른 응답을 주면 무엇이 가입 가능한 번호인지 흘린다. 정규화는 서비스가 한다.
 *
 * <p>비밀번호에도 형식 검증을 걸지 않는다. 정책이 바뀌기 전에 가입한 사람이 <b>로그인
 * 단계에서 막히면</b> 들어와서 바꿀 방법조차 없어진다. 검증은 설정할 때만 한다.
 */
@Schema(name = "PasswordSignInRequest", description = "휴대폰 로그인")
public record PasswordSignInRequest(
        @Schema(description = "휴대폰번호. 하이픈이 있어도 된다", example = "010-1234-5678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "휴대폰번호를 입력해 주세요.")
        @Size(max = 20)
        String phone,

        @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72)
        String password
) {}
