package com.hanseongge.domain.admin.entity;

/** 관리자 계정 상태. V1 마이그레이션의 CHECK 제약과 값이 일치해야 한다. */
public enum AdminStatus {
    ACTIVE,
    /** 로그인 5회 실패 등으로 잠김 (설계서 §7.1) */
    LOCKED,
    DISABLED
}
