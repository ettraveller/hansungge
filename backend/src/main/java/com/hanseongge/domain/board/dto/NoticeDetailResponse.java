package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 공지 상세 (공개). */
@Schema(name = "NoticeDetailResponse", description = "공지 상세 (공개)")
public record NoticeDetailResponse(

        @Schema(description = "공지 ID", example = "12")
        Long id,

        @Schema(description = "제목", example = "추석 연휴 배송 안내")
        String title,

        @Schema(description = "본문", example = "9월 15일부터 17일까지 배송이 중단됩니다.")
        String content,

        @Schema(description = "상단 고정 여부", example = "false")
        boolean pinned,

        @Schema(description = "조회수", example = "1204")
        int viewCount,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt,

        @Schema(description = "수정 시각. 조회수 증가로는 바뀌지 않는다")
        OffsetDateTime updatedAt
) {

    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.getViewCount(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}
