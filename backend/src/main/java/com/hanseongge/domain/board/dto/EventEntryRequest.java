package com.hanseongge.domain.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이벤트 응모 요청 (회원). */
@Schema(name = "EventEntryRequest", description = "이벤트 응모 요청")
public record EventEntryRequest(

        @Schema(description = "응모 내용", example = "기프티콘 신청합니다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "응모 내용을 입력해 주세요.")
        @Size(max = 2000, message = "응모 내용은 2000자를 넘을 수 없습니다.")
        String content,

        @Schema(description = "비밀 응모글 여부. 기본은 비밀이다 — 연락처가 섞이기 쉽다",
                example = "true")
        Boolean secret
) {

    /** 기본이 {@code true} 다. 실수로 공개되는 쪽이 아니라 일부러 공개하는 쪽이 되게 한다. */
    public boolean secretOrDefault() {
        return secret == null || secret;
    }
}
