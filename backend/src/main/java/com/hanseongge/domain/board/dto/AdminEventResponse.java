package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Event;
import com.hanseongge.domain.board.entity.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 이벤트 (관리자). 공개 응답과 달리 {@code active} 가 있다. */
@Schema(name = "AdminEventResponse", description = "이벤트 (관리자)")
public record AdminEventResponse(

        Long id,
        String title,
        String content,
        String thumbnailKey,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        EventStatus status,
        int viewCount,
        long entryCount,

        @Schema(description = "노출 여부", example = "true")
        boolean active,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static AdminEventResponse of(Event event, long entryCount) {
        return new AdminEventResponse(
                event.getId(),
                event.getTitle(),
                event.getContent(),
                event.getThumbnailKey(),
                event.getStartAt(),
                event.getEndAt(),
                event.status(),
                event.getViewCount(),
                entryCount,
                event.isActive(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
