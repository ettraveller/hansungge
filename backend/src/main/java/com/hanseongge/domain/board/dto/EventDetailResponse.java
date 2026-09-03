package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Event;
import com.hanseongge.domain.board.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 이벤트 상세 (공개). */
@Schema(name = "EventDetailResponse", description = "이벤트 상세 (공개)")
public record EventDetailResponse(

        @Schema(description = "이벤트 ID", example = "3")
        Long id,

        @Schema(description = "제목")
        String title,

        @Schema(description = "본문")
        String content,

        @Schema(description = "대표 이미지 키")
        String thumbnailKey,

        @Schema(description = "시작 시각")
        OffsetDateTime startAt,

        @Schema(description = "종료 시각")
        OffsetDateTime endAt,

        @Schema(description = "진행 상태", example = "OPEN")
        EventStatus status,

        @Schema(description = "조회수")
        int viewCount,

        @Schema(description = "응모 건수")
        long entryCount,

        @Schema(description = "로그인한 회원이 이미 응모했는가. 응모 버튼의 근거다",
                example = "false")
        boolean entered
) {

    public static EventDetailResponse of(Event event, long entryCount, boolean entered) {
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getContent(),
                event.getThumbnailKey(),
                event.getStartAt(),
                event.getEndAt(),
                event.status(),
                event.getViewCount(),
                entryCount,
                entered);
    }
}
