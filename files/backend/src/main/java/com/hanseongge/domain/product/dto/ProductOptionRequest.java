package com.hanseongge.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 옵션 등록·수정 요청. */
@Schema(name = "ProductOptionRequest", description = "상품 옵션 등록/수정 요청")
public record ProductOptionRequest(

        @Schema(description = "옵션명", example = "7.5g (2돈)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "옵션명을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @Schema(description = "중량(g). 비우면 상품 중량을 쓴다", example = "7.5")
        @DecimalMin(value = "0.0001", message = "중량은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 4)
        BigDecimal weightG,

        @Schema(description = "추가 공임(원). MARKET_PLUS 에서만 가격에 반영된다", example = "20000")
        @DecimalMin(value = "0", message = "추가 공임은 0 이상이어야 합니다.")
        @Digits(integer = 13, fraction = 2)
        BigDecimal additionalLaborCost,

        @Schema(description = "초기 재고 (등록 시에만 사용). 수정은 재고 조정 API 를 쓴다", example = "10")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer stock,

        @Schema(description = "정렬 순서", example = "10")
        int sort,

        @Schema(description = "활성 여부. 마지막 활성 옵션은 끌 수 없다", example = "true")
        Boolean active
) {

    public boolean activeOrDefault() {
        return active == null || active;
    }

    public int stockOrZero() {
        return stock == null ? 0 : stock;
    }
}
