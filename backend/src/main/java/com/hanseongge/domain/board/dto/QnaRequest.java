package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.QnaBoardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 문의 등록·수정 요청 (회원). */
@Schema(name = "QnaRequest", description = "문의 등록/수정 요청")
public record QnaRequest(

        @Schema(description = "소속 게시판. 수정 시에는 무시된다", example = "GENERAL",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "게시판을 선택해 주세요.")
        QnaBoardType boardType,

        @Schema(description = "제목", example = "배송은 얼마나 걸리나요",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @Schema(description = "문의 내용", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "문의 내용을 입력해 주세요.")
        @Size(max = 4000, message = "문의 내용은 4000자를 넘을 수 없습니다.")
        String question,

        @Schema(description = "비밀글로 등록할지. 본문을 작성자와 관리자만 볼 수 있다",
                example = "false")
        Boolean secret,

        @Schema(description = "연결할 상품 ID. 제품문의에서만 쓴다", example = "3")
        Long productId
) {

    public boolean secretOrDefault() {
        return secret != null && secret;
    }

    /**
     * 문의게시판 글에는 상품을 붙이지 않는다.
     *
     * <p>붙여 두면 제품문의 목록에는 안 나오는데 상품 상세에는 걸리는, 어느 쪽에서도
     * 설명되지 않는 글이 생긴다.
     */
    public Long productIdFor(QnaBoardType type) {
        return type == QnaBoardType.PRODUCT ? productId : null;
    }
}
