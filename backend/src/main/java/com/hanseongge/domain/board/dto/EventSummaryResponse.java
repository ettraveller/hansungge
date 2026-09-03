package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Event;
import com.hanseongge.domain.board.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 이벤트 목록 항목 (공개). */
@Schema(name = "EventSummaryResponse", description = "이벤트 목록 항목 (공개)")
public record EventSummaryResponse(

        @Schema(description = "이벤트 ID", example = "3")
        Long id,

        @Schema(description = "제목", example = "9월 황금감사제")
        String title,

        @Schema(description = "대표 이미지 Object Storage 키")
        String thumbnailKey,

        @Schema(description = "시작 시각")
        OffsetDateTime startAt,

        @Schema(description = "종료 시각")
        OffsetDateTime endAt,

        @Schema(description = "진행 상태. 저장값이 아니라 기간과 현재 시각으로 계산한다",
                example = "OPEN")
        EventStatus status,

        @Schema(description = "조회수", example = "1908")
        int viewCount,

        @Schema(description = "응모 건수", example = "24")
        long entryCount
) {

    public static EventSummaryResponse of(Event event, long entryCount) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getThumbnailKey(),
                event.getStartAt(),
                event.getEndAt(),
                event.status(),
                event.getViewCount(),
                entryCount);
    }
}
