package com.hanseongge.domain.board.entity;

/**
 * 이벤트 진행 상태.
 *
 * <p>DB 에 저장하지 않는다. {@link Event#status()} 가 기간과 현재 시각으로 계산한다 —
 * 저장하면 기간이 지나도 누가 값을 바꿔 주기 전까지 "진행중"으로 남는다.
 */
public enum EventStatus {

    /** 시작 전 */
    UPCOMING,

    /** 진행 중 — 응모를 받는 유일한 상태다 */
    OPEN,

    /** 종료 */
    CLOSED
}
