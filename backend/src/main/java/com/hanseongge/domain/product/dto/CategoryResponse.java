package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.product.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 카테고리 응답. */
@Schema(name = "CategoryResponse", description = "카테고리")
public record CategoryResponse(

        @Schema(description = "카테고리 ID", example = "1")
        Long id,

        @Schema(description = "상위 카테고리 ID", example = "null")
        Long parentId,

        @Schema(description = "카테고리명", example = "골드바")
        String name,

        @Schema(description = "정렬 순서", example = "10")
        int sort,

        @Schema(description = "활성 여부", example = "true")
        boolean active,

        @Schema(description = "마지막 수정 시각")
        OffsetDateTime updatedAt
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getSort(),
                category.isActive(),
                category.getUpdatedAt());
    }
}
