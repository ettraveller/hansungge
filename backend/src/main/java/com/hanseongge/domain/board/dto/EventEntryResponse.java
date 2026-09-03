package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.EventEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 이벤트 응모글 (공개).
 *
 * <p>비밀 응모글이면 {@link #content} 가 <b>비어서</b> 나간다. 목록에서 응모글을 통째로
 * 빼지 않는 이유는, 몇 명이 응모했는지가 보여야 참여를 유도하기 때문이다 — 레퍼런스도
 * 자물쇠만 붙여 목록에 남긴다.
 */
@Schema(name = "EventEntryResponse", description = "이벤트 응모글 (공개)")
public record EventEntryResponse(

        @Schema(description = "응모 ID", example = "17")
        Long id,

        @Schema(description = "가려진 작성자명", example = "김**")
        String authorName,

        @Schema(description = "비밀 응모글 여부", example = "true")
        boolean secret,

        @Schema(description = "응모 내용. 비밀글을 남이 보면 없다")
        String content,

        @Schema(description = "응모 시각")
        OffsetDateTime createdAt,

        @Schema(description = "로그인한 회원 본인의 응모인가", example = "false")
        boolean mine
) {

    public static EventEntryResponse of(EventEntry entry, String maskedName,
                                        Long viewerId, boolean admin) {
        boolean readable = entry.isReadableBy(viewerId, admin);
        return new EventEntryResponse(
                entry.getId(),
                maskedName,
                entry.isSecret(),
                readable ? entry.getContent() : null,
                entry.getCreatedAt(),
                entry.isOwnedBy(viewerId));
    }
}
