package com.hanseongge.domain.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 관리 화면 게시판 탭의 미답변 건수.
 *
 * <p>내린 글은 세지 않는다 — 대시보드의 미답변 총건수 배지와 같은 기준이어야
 * 두 숫자가 어긋나지 않는다. 필드가 화면의 게시판 탭(전체·문의게시판·제품문의)과
 * 1:1 이다.
 */
@Schema(name = "AdminQnaUnansweredCounts", description = "게시판별 미답변 문의 건수")
public record AdminQnaUnansweredCounts(

        @Schema(description = "전체 게시판") long all,
        @Schema(description = "문의게시판") long general,
        @Schema(description = "제품문의") long product
) {
}
