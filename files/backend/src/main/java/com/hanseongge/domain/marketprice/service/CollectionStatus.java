package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.FetchResult;

import java.time.OffsetDateTime;

/**
 * 시세 수집 상태 — 관리자 대시보드의 "수집 상태 / 마지막 성공 시각" (개발계획 §12.2 L1-b).
 *
 * <p>{@code lastSuccessAt} 이 오래됐는데 {@code lastAttemptAt} 은 최근이면 <b>수집기는 돌고
 * 있는데 계속 실패하고 있다</b>는 뜻이다. 두 값을 함께 보여줘야 그 상태를 구분할 수 있다.
 *
 * @param schedulerEnabled 스케줄러가 켜져 있는가
 * @param cron             현재 적용 중인 수집 주기
 * @param lastAttemptAt    마지막 시도 시각 (성공·실패 무관). 없으면 null
 * @param lastAttemptResult 마지막 시도 결과. 없으면 null
 * @param lastSuccessAt    마지막 <b>성공</b> 시각. 없으면 null
 * @param failureCount     누적 실패 건수
 */
public record CollectionStatus(
        boolean schedulerEnabled,
        String cron,
        OffsetDateTime lastAttemptAt,
        FetchResult lastAttemptResult,
        OffsetDateTime lastSuccessAt,
        long failureCount
) {

    /** 한 번도 성공한 적 없는가. 대시보드 적색 경고 조건이다. */
    public boolean neverSucceeded() {
        return lastSuccessAt == null;
    }
}
