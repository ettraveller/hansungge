package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.service.CollectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 수집 상태 (설계서 §6.2 — 대시보드 수집상태 / 최종 성공시각).
 *
 * <p>{@code lastSuccessAt} 만 보면 "수집기가 죽었는지, 돌면서 계속 실패하는지"를 구분할 수
 * 없다. {@code lastAttemptAt} 을 함께 내려주는 이유다.
 */
@Schema(name = "CollectionStatusResponse", description = "시세 수집 상태")
public record CollectionStatusResponse(

        @Schema(description = "스케줄러 활성 여부", example = "true")
        boolean schedulerEnabled,

        @Schema(description = "적용 중인 수집 주기(cron)", example = "0 0/30 10-18 * * MON-FRI")
        String cron,

        @Schema(description = "마지막 시도 시각 (성공·실패 무관)")
        OffsetDateTime lastAttemptAt,

        @Schema(description = "마지막 시도 결과", example = "SUCCESS")
        FetchResult lastAttemptResult,

        @Schema(description = "마지막 성공 시각")
        OffsetDateTime lastSuccessAt,

        @Schema(description = "누적 실패 건수", example = "0")
        long failureCount,

        @Schema(description = "한 번도 성공한 적 없는가. true 면 대시보드 적색 경고", example = "false")
        boolean neverSucceeded,

        @Schema(description = "상태 설명")
        String message
) {

    public static CollectionStatusResponse from(CollectionStatus status) {
        return new CollectionStatusResponse(
                status.schedulerEnabled(),
                status.cron(),
                status.lastAttemptAt(),
                status.lastAttemptResult(),
                status.lastSuccessAt(),
                status.failureCount(),
                status.neverSucceeded(),
                describe(status));
    }

    private static String describe(CollectionStatus status) {
        if (!status.schedulerEnabled()) {
            return "자동 수집 스케줄러가 비활성 상태입니다. 시세는 관리자 수동 고시로만 갱신됩니다.";
        }
        if (status.neverSucceeded() && status.lastAttemptAt() == null) {
            return "아직 수집 시도가 없습니다. 자동 수집 소스(KRX)는 약관 확인 전까지 비활성입니다.";
        }
        if (status.neverSucceeded()) {
            return "수집을 시도했으나 아직 성공한 적이 없습니다. 시세는 수동 고시 값이 유지됩니다.";
        }
        return "정상";
    }
}
