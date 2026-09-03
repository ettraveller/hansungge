package com.hanseongge.domain.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 등록·수정 요청 (관리자). */
@Schema(name = "NoticeRequest", description = "공지 등록/수정 요청")
public record NoticeRequest(

        @Schema(description = "제목", example = "추석 연휴 배송 안내",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @Schema(description = "본문", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "내용을 입력해 주세요.")
        String content,

        @Schema(description = "상단 고정 여부", example = "false")
        Boolean pinned,

        @Schema(description = "노출 여부. 등록 시에는 무시되고 항상 노출로 시작한다", example = "true")
        Boolean active
) {

    public boolean pinnedOrDefault() {
        return pinned != null && pinned;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
