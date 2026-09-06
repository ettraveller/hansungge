package com.hanseongge.domain.user.entity;

/**
 * 이메일 인증을 한 이유.
 *
 * <p>{@link IdentityPurpose} 와 같은 이유로 나눈다 — 가입용으로 받은 인증번호가
 * 비밀번호 재설정에 통하면 안 된다.
 *
 * <p>{@code email_verifications.purpose} CHECK 제약과 값이 일치해야 한다 (V13).
 */
public enum EmailVerificationPurpose {

    SIGNUP,
    PASSWORD_RESET,
    /** 마이페이지에서 이메일을 바꿀 때. 아직 쓰는 곳이 없다. */
    CHANGE_EMAIL
}
