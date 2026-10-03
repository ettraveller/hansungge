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
    PASSWORD_RESET,
    /**
     * 로그인한 회원이 마이페이지에서 이메일을 등록·변경한다.
     *
     * <p>{@link #EMAIL_VERIFY} 와 묻는 것은 같지만 쓰는 사람이 다르다 — 저쪽은 가입 중
     * (로그인 전), 이쪽은 로그인한 회원이다. 같은 값으로 두면 한쪽에서 받은 번호가
     * 다른 쪽에서 통한다.
     */
    EMAIL_CHANGE
}
