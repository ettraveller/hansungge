package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 자체 가입 중 간편인증을 마친 뒤 — 다음에 무엇을 할지.
 *
 * <p>화면이 순서를 추측하지 않게 서버가 {@link Step} 으로 정해 준다. 서버 정책이 바뀔 때
 * 화면의 추측이 조용히 어긋나는 일을 막는다.
 */
@Schema(name = "IdentityResultResponse", description = "간편인증 뒤 다음 단계")
public record IdentityResultResponse(

        @Schema(description = "다음 단계")
        Step step,

        @Schema(description = "SET_PASSWORD 에서 다음 요청에 쓸 진행 토큰")
        String signUpToken,

        @Schema(description = "EXISTING_ACCOUNT 에서 바로 비밀번호를 다시 정할 때 쓰는 재설정 토큰(5분)")
        String resetToken,

        @Schema(description = "이미 있는 계정의 이메일(가림)")
        String accountEmail,

        @Schema(description = "이미 있는 계정의 휴대폰(가림)")
        String accountPhone,

        @Schema(description = "이미 있는 계정에 비밀번호가 있는가")
        Boolean accountHasPassword,

        @Schema(description = "SET_PASSWORD 에서 보여줄 본인인증 이름 (본인 것)")
        String name,

        @Schema(description = "SET_PASSWORD 에서 보여줄 본인인증 휴대폰 (본인 것, 숫자만)")
        String phone
) {

    /**
     * 소셜 가입은 본인인증을 받지 않으므로(2026-09-21) 여기 오는 것은 자체 가입뿐이다 —
     * 이전의 소셜 연결 확인(CONFIRM_LINK)·소셜 가입 완료(SIGNED_IN) 단계는 없어졌다.
     */
    public enum Step {
        /** 새 회원 — 비밀번호와 선택 이메일을 받는다 */
        SET_PASSWORD,
        /** 이미 가입한 사람 — 로그인하거나 바로 비밀번호를 다시 정한다 */
        EXISTING_ACCOUNT
    }

    public static IdentityResultResponse setPassword(String signUpToken, String name, String phone) {
        return new IdentityResultResponse(Step.SET_PASSWORD, signUpToken, null,
                null, null, null, name, phone);
    }

    public static IdentityResultResponse existing(String resetToken, String email, String phone,
                                                  boolean hasPassword) {
        return new IdentityResultResponse(Step.EXISTING_ACCOUNT, null, resetToken,
                email, phone, hasPassword, null, null);
    }
}
