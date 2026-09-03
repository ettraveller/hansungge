package com.hanseongge.domain.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 문의 답변 요청 (관리자). */
@Schema(name = "QnaAnswerRequest", description = "문의 답변 등록/수정 요청")
public record QnaAnswerRequest(

        @Schema(description = "답변 내용", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "답변 내용을 입력해 주세요.")
        @Size(max = 4000, message = "답변은 4000자를 넘을 수 없습니다.")
        String answer
) {
}
