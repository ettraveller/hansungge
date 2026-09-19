package com.hanseongge.domain.user.entity;

/**
 * 인증번호를 보내는 길. {@code verification_codes.channel} CHECK 제약과 값이 일치해야 한다 (V22).
 */
public enum VerificationChannel {

    EMAIL,
    SMS
}
