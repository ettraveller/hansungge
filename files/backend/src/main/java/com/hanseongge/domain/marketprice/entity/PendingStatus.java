package com.hanseongge.domain.marketprice.entity;

/** 이상 변동 승인 대기 상태. V2 마이그레이션의 CHECK 제약과 값이 일치해야 한다. */
public enum PendingStatus {

    /** 승인 대기. 이 상태에서는 <b>effective 가 발행되지 않고 기존 시세가 유지된다.</b> */
    PENDING,
    APPROVED,
    REJECTED
}
