package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 문의 목록 한 줄 (공개).
 *
 * <p>{@code question} 과 {@code answer} 본문이 없다. 비밀글 본문이 목록 응답에 실려
 * 나가는 사고를 <b>구조로</b> 막는다 — 필드가 없으면 실수로 담을 수도 없다.
 *
 * <p>{@link #authorName} 은 이미 가려진 값이다.
 */
@Schema(name = "QnaSummaryResponse", description = "문의 목록 항목 (공개)")
public record QnaSummaryResponse(

        @Schema(description = "문의 ID", example = "42")
        Long id,

        @Schema(description = "소속 게시판", example = "GENERAL")
        QnaBoardType boardType,

        @Schema(description = "제목", example = "배송은 얼마나 걸리나요")
        String title,

        @Schema(description = "가려진 작성자명", example = "김**")
        String authorName,

        @Schema(description = "비밀글 여부. 본문은 작성자와 관리자만 볼 수 있다", example = "false")
        boolean secret,

        @Schema(description = "답변 상태", example = "PENDING", allowableValues = {"PENDING", "ANSWERED"})
        String status,

        @Schema(description = "조회수", example = "17")
        int viewCount,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt,

        @Schema(description = "연결된 상품 ID. 제품문의에서 상품을 고른 경우에만 있다", example = "3")
        Long productId,

        @Schema(description = "연결된 상품명", example = "순금 골드바 3.75g (1돈)")
        String productName
) {

    public static QnaSummaryResponse of(Qna qna, String maskedName, String productName) {
        return new QnaSummaryResponse(
                qna.getId(),
                qna.getBoardType(),
                qna.getTitle(),
                maskedName,
                qna.isSecret(),
                qna.isAnswered() ? "ANSWERED" : "PENDING",
                qna.getViewCount(),
                qna.getCreatedAt(),
                qna.getProductId(),
                productName);
    }
}
