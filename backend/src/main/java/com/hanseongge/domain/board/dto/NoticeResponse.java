package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 공지 (관리자).
 *
 * <p>공개 응답과 나누는 이유는 여기에만 {@code active}·{@code createdBy} 가 있기 때문이다.
 * 한 DTO 로 합치고 화면에서 감추면, 내린 공지의 존재와 작성 관리자 아이디가 공개 API
 * 응답에 그대로 실려 나간다.
 */
@Schema(name = "NoticeResponse", description = "공지 (관리자)")
public record NoticeResponse(

        @Schema(description = "공지 ID", example = "12")
        Long id,

        @Schema(description = "제목", example = "추석 연휴 배송 안내")
        String title,

        @Schema(description = "본문")
        String content,

        @Schema(description = "상단 고정 여부", example = "false")
        boolean pinned,

        @Schema(description = "조회수", example = "1204")
        int viewCount,

        @Schema(description = "노출 여부", example = "true")
        boolean active,

        @Schema(description = "작성 관리자 로그인 아이디", example = "admin")
        String createdBy,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt,

        @Schema(description = "수정 시각")
        OffsetDateTime updatedAt
) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.getViewCount(),
                notice.isActive(),
                notice.getCreatedBy(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}
