package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가입 중 본인확인을 마친 뒤 — 다음에 무엇을 할지.
 *
 * <p>화면이 순서를 추측하지 않게 서버가 {@link Step} 으로 정해 준다. 서버 정책이 바뀔 때
 * 화면의 추측이 조용히 어긋나는 일을 막는다.
 */
@Schema(name = "IdentityResultResponse", description = "본인확인 뒤 다음 단계")
public record IdentityResultResponse(

        @Schema(description = "다음 단계")
        Step step,

        @Schema(description = "SIGNED_IN 일 때 로그인 결과")
        SignInResponse session,

        @Schema(description = "SET_PASSWORD · CONFIRM_LINK 에서 다음 요청에 쓸 진행 토큰")
        String signUpToken,

        @Schema(description = "EXISTING_ACCOUNT 에서 바로 비밀번호를 다시 정할 때 쓰는 재설정 토큰(5분)")
        String resetToken,

        @Schema(description = "이미 있는 계정의 이메일(가림)")
        String accountEmail,

        @Schema(description = "이미 있는 계정의 휴대폰(가림)")
        String accountPhone,

        @Schema(description = "이미 있는 계정에 비밀번호가 있는가")
        Boolean accountHasPassword,

        @Schema(description = "SET_PASSWORD 에서 보여줄 본인확인 이름 (본인 것)")
        String name,

        @Schema(description = "SET_PASSWORD 에서 보여줄 본인확인 휴대폰 (본인 것, 숫자만)")
        String phone
) {

    public enum Step {
        /** 새 회원(자체 가입) — 비밀번호와 선택 이메일을 받는다 */
        SET_PASSWORD,
        /** 이미 가입한 사람(자체 가입 시도) — 로그인하거나 바로 비밀번호를 다시 정한다 */
        EXISTING_ACCOUNT,
        /** 이미 가입한 사람(소셜 가입 시도) — 이 소셜 계정을 그 계정에 연결할지 묻는다 */
        CONFIRM_LINK,
        /** 새 회원(소셜 가입) — 가입을 마치고 로그인했다 */
        SIGNED_IN
    }

    public static IdentityResultResponse setPassword(String signUpToken, String name, String phone) {
        return new IdentityResultResponse(Step.SET_PASSWORD, null, signUpToken, null,
                null, null, null, name, phone);
    }

    public static IdentityResultResponse existing(String resetToken, String email, String phone,
                                                  boolean hasPassword) {
        return new IdentityResultResponse(Step.EXISTING_ACCOUNT, null, null, resetToken,
                email, phone, hasPassword, null, null);
    }

    public static IdentityResultResponse confirmLink(String signUpToken, String email, String phone) {
        return new IdentityResultResponse(Step.CONFIRM_LINK, null, signUpToken, null,
                email, phone, null, null, null);
    }

    public static IdentityResultResponse signedIn(SignInResponse session) {
        return new IdentityResultResponse(Step.SIGNED_IN, session, null, null,
                null, null, null, null, null);
    }
}
