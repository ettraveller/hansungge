package com.hanseongge.domain.audit.entity;

/**
 * 감사로그 행위 주체 구분. V1 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 */
public enum ActorType {

    /** 관리자 계정 ({@code admin_users.login_id}) */
    ADMIN,
    /** 일반 사용자 (STEP 6 소셜 로그인 이후 사용) */
    USER,
    /** 배치·스케줄러 등 사람이 아닌 주체 */
    SYSTEM
}
