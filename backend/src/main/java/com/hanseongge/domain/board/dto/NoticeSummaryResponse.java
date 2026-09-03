package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 공지 목록 한 줄 (공개).
 *
 * <p>본문을 싣지 않는다. 목록은 한 번에 20건이 나가는데 공지 본문은 길고, 화면이 쓰지도
 * 않는 값을 스무 벌 실어 보낼 이유가 없다.
 *
 * <p>{@code createdBy}(관리자 로그인 아이디)도 나가지 않는다. 관리자 계정 이름은 외부에
 * 알릴 정보가 아니다 — 화면에는 "관리자"로 고정 표시한다.
 */
@Schema(name = "NoticeSummaryResponse", description = "공지 목록 항목 (공개)")
public record NoticeSummaryResponse(

        @Schema(description = "공지 ID", example = "12")
        Long id,

        @Schema(description = "제목", example = "추석 연휴 배송 안내")
        String title,

        @Schema(description = "상단 고정 여부. 목록 맨 위에 모아 보인다", example = "false")
        boolean pinned,

        @Schema(description = "조회수", example = "1204")
        int viewCount,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt
) {

    public static NoticeSummaryResponse from(Notice notice) {
        return new NoticeSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.isPinned(),
                notice.getViewCount(),
                notice.getCreatedAt());
    }
}
