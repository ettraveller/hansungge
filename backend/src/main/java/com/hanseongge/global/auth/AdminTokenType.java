package com.hanseongge.global.auth;

/**
 * 관리자 토큰 종류. JWT 의 {@code typ} 클레임에 담긴다.
 *
 * <p>구분을 두는 이유: Refresh 토큰은 수명이 훨씬 길다(8시간). 이 토큰으로 일반 API 를
 * 그대로 호출할 수 있으면 Access 토큰을 30분으로 짧게 잡은 의미가 사라진다.
 *
 * <p>OTP_* 는 비밀번호만 맞힌 단계의 표다. 관리자 API 필터는 ACCESS 만 받으므로 이 표로는
 * 6자리 입력·인증 앱 등록 말고 아무것도 할 수 없다.
 */
public enum AdminTokenType {

    ACCESS,
    REFRESH,
    /** 비밀번호 통과, 6자리 대기 (5분) */
    OTP_CHALLENGE,
    /** 비밀번호 통과, 인증 앱 등록 대기 (15분 — QR 을 찍고 앱을 설치할 시간) */
    OTP_SETUP;

    public String claimValue() {
        return name().toLowerCase();
    }
}
