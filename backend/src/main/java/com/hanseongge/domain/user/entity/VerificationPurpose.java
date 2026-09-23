package com.hanseongge.domain.user.entity;

/**
 * 인증번호를 보낸 이유.
 *
 * <p>{@link IdentityPurpose} 와 같은 이유로 나눈다 — 이메일 확인용으로 받은 번호가 로그인에
 * 통하면 안 된다. 채널과 함께 대조한다.
 *
 * <p>{@code verification_codes.purpose} CHECK 제약과 값이 일치해야 한다 (V24).
 */
public enum VerificationPurpose {

    /** 문자·메일로 받은 번호로 로그인 */
    LOGIN,
    /** 가입 시 입력한 이메일이 본인 것인지 확인 */
    EMAIL_VERIFY,
    /** 비밀번호 찾기 — 이 번호를 통과하면 새 비밀번호를 정할 수 있다 */
    PASSWORD_RESET
}
