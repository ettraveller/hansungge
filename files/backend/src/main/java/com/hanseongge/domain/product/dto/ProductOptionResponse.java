package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.product.entity.ProductOption;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 상품 옵션 응답.
 *
 * <p>공개 응답에서는 {@code stock} 대신 {@link #inStock} 만 노출한다 — 정확한 재고 수량은
 * 영업 정보이고, 고객이 알아야 할 것은 "살 수 있는가" 뿐이다.
 */
@Schema(name = "ProductOptionResponse", description = "상품 옵션")
public record ProductOptionResponse(

        @Schema(description = "옵션 ID", example = "1")
        Long id,

        @Schema(description = "옵션명", example = "3.75g (1돈)")
        String name,

        @Schema(description = "중량(g). null 이면 상품 중량을 쓴다", example = "3.7500")
        BigDecimal weightG,

        @Schema(description = "추가 공임(원). MARKET_PLUS 에서만 가격에 반영된다", example = "0.00")
        BigDecimal additionalLaborCost,

        @Schema(description = "재고 수량. **관리자 전용** — 공개 응답에서는 null", example = "10")
        Integer stock,

        @Schema(description = "구매 가능 여부", example = "true")
        boolean inStock,

        @Schema(description = "정렬 순서", example = "0")
        int sort,

        @Schema(description = "활성 여부", example = "true")
        boolean active
) {

    public static ProductOptionResponse forAdmin(ProductOption option) {
        return of(option, option.getStock());
    }

    /** 공개용 — 재고 수량을 숨긴다. */
    public static ProductOptionResponse forPublic(ProductOption option) {
        return of(option, null);
    }

    private static ProductOptionResponse of(ProductOption option, Integer stock) {
        return new ProductOptionResponse(
                option.getId(),
                option.getName(),
                option.getWeightG(),
                option.getAdditionalLaborCost(),
                stock,
                option.isInStock(),
                option.getSort(),
                option.isActive());
    }
}
