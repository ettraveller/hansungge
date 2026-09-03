package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 문의 (관리자).
 *
 * <p>공개 응답과 나누는 이유는 여기에만 <b>가리지 않은 작성자명</b>과 {@code active},
 * {@code answeredBy} 가 있기 때문이다. 하나로 합치고 화면에서 감추면 실명이 공개 API
 * 응답에 그대로 실려 나간다.
 *
 * <p>관리자가 실명을 보는 것은 필요한 일이다 — 같은 사람이 여러 번 문의했는지, 주문
 * 이력과 연결되는지를 판단해야 답변할 수 있다.
 */
@Schema(name = "AdminQnaResponse", description = "문의 (관리자)")
public record AdminQnaResponse(

        @Schema(description = "문의 ID", example = "42")
        Long id,

        @Schema(description = "소속 게시판", example = "GENERAL")
        QnaBoardType boardType,

        @Schema(description = "제목")
        String title,

        @Schema(description = "질문 본문")
        String question,

        @Schema(description = "작성 회원 ID", example = "7")
        Long userId,

        @Schema(description = "작성자명 (가리지 않음)", example = "김민준")
        String authorName,

        @Schema(description = "비밀글 여부", example = "false")
        boolean secret,

        @Schema(description = "답변 상태", example = "PENDING")
        String status,

        @Schema(description = "답변 본문")
        String answer,

        @Schema(description = "답변한 관리자 로그인 아이디", example = "admin")
        String answeredBy,

        @Schema(description = "답변 시각")
        OffsetDateTime answeredAt,

        @Schema(description = "조회수", example = "17")
        int viewCount,

        @Schema(description = "노출 여부", example = "true")
        boolean active,

        @Schema(description = "연결된 상품 ID", example = "3")
        Long productId,

        @Schema(description = "연결된 상품명")
        String productName,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt
) {

    public static AdminQnaResponse of(Qna qna, String authorName, String productName) {
        return new AdminQnaResponse(
                qna.getId(),
                qna.getBoardType(),
                qna.getTitle(),
                qna.getQuestion(),
                qna.getUserId(),
                authorName,
                qna.isSecret(),
                qna.isAnswered() ? "ANSWERED" : "PENDING",
                qna.getAnswer(),
                qna.getAnsweredBy(),
                qna.getAnsweredAt(),
                qna.getViewCount(),
                qna.isActive(),
                qna.getProductId(),
                productName,
                qna.getCreatedAt());
    }
}
