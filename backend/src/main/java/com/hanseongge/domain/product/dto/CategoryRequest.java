package com.hanseongge.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 카테고리 등록·수정 요청. */
@Schema(name = "CategoryRequest", description = "카테고리 등록/수정 요청")
public record CategoryRequest(

        @Schema(description = "상위 카테고리 ID. 최상위면 null", example = "1")
        Long parentId,

        @Schema(description = "카테고리명", example = "골드바", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "카테고리명을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @Schema(description = "정렬 순서 (작을수록 앞)", example = "10")
        int sort,

        @Schema(description = "활성 여부. 비활성이면 공개 목록에서 빠진다", example = "true")
        Boolean active
) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
