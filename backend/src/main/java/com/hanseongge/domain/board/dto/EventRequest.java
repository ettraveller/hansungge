package com.hanseongge.domain.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** 이벤트 등록·수정 요청 (관리자). */
@Schema(name = "EventRequest", description = "이벤트 등록/수정 요청")
public record EventRequest(

        @Schema(description = "제목", example = "9월 황금감사제",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @Schema(description = "본문")
        String content,

        @Schema(description = "대표 이미지 Object Storage 키")
        @Size(max = 300)
        String thumbnailKey,

        @Schema(description = "시작 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "시작 시각을 입력해 주세요.")
        OffsetDateTime startAt,

        @Schema(description = "종료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "종료 시각을 입력해 주세요.")
        OffsetDateTime endAt,

        @Schema(description = "노출 여부", example = "true")
        Boolean active
) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
