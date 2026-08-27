package com.hanseongge.global.auth;

/**
 * 관리자 토큰 종류. JWT 의 {@code typ} 클레임에 담긴다.
 *
 * <p>구분을 두는 이유: Refresh 토큰은 수명이 훨씬 길다(8시간). 이 토큰으로 일반 API 를
 * 그대로 호출할 수 있으면 Access 토큰을 30분으로 짧게 잡은 의미가 사라진다.
 */
public enum AdminTokenType {

    ACCESS,
    REFRESH;

    public String claimValue() {
        return name().toLowerCase();
    }
}
