package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 문의 상세 (공개).
 *
 * <p>본문({@link #question}·{@link #answer})은 <b>볼 수 있는 사람에게만</b> 채워진다.
 * 비밀글을 남이 열면 애초에 404 라 이 응답 자체가 나가지 않지만, 필드를 채우는 책임을
 * 서비스 한 곳에 모아 두면 나중에 조건이 바뀌어도 고칠 자리가 하나다.
 *
 * <p>{@code answeredBy}(관리자 로그인 아이디)는 나가지 않는다. 고객에게 필요한 것은
 * "답변이 달렸다"이지 누가 달았는지가 아니다.
 */
@Schema(name = "QnaDetailResponse", description = "문의 상세 (공개)")
public record QnaDetailResponse(

        @Schema(description = "문의 ID", example = "42")
        Long id,

        @Schema(description = "소속 게시판", example = "GENERAL")
        QnaBoardType boardType,

        @Schema(description = "제목", example = "배송은 얼마나 걸리나요")
        String title,

        @Schema(description = "가려진 작성자명", example = "김**")
        String authorName,

        @Schema(description = "비밀글 여부", example = "false")
        boolean secret,

        @Schema(description = "답변 상태", example = "ANSWERED")
        String status,

        @Schema(description = "조회수", example = "17")
        int viewCount,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt,

        @Schema(description = "연결된 상품 ID", example = "3")
        Long productId,

        @Schema(description = "연결된 상품명")
        String productName,

        @Schema(description = "질문 본문")
        String question,

        @Schema(description = "답변. 미답변이면 없다")
        String answer,

        @Schema(description = "답변 시각")
        OffsetDateTime answeredAt,

        @Schema(description = "로그인한 회원이 이 글의 작성자인가. 수정·삭제 버튼의 근거다",
                example = "false")
        boolean mine
) {

    public static QnaDetailResponse of(Qna qna, String maskedName, String productName, boolean mine) {
        return new QnaDetailResponse(
                qna.getId(),
                qna.getBoardType(),
                qna.getTitle(),
                maskedName,
                qna.isSecret(),
                qna.isAnswered() ? "ANSWERED" : "PENDING",
                qna.getViewCount(),
                qna.getCreatedAt(),
                qna.getProductId(),
                productName,
                qna.getQuestion(),
                qna.getAnswer(),
                qna.getAnsweredAt(),
                mine);
    }
}
