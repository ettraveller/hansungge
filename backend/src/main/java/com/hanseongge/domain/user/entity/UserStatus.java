package com.hanseongge.domain.user.entity;

/** 회원 상태. V1 {@code users.status} CHECK 제약과 값이 일치해야 한다. */
public enum UserStatus {

    ACTIVE("정상"),
    /** 장기 미접속 휴면. 재활성화하면 ACTIVE 로 돌아온다 */
    DORMANT("휴면"),
    WITHDRAWN("탈퇴"),
    BLOCKED("이용정지");

    private final String displayName;

    UserStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 로그인과 API 사용이 가능한가. 휴면은 재활성화를 거쳐야 하므로 여기서는 false 다. */
    public boolean canSignIn() {
        return this == ACTIVE;
    }
}
