package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 상품 검색 조건. 모든 필드가 선택이다.
 *
 * <p>{@code onlyVisible} 은 <b>공개 조회에서 서버가 강제로 켠다.</b> 클라이언트가 끌 수 있게
 * 두면 숨김 상품을 파라미터 하나로 열람할 수 있다.
 */
@Schema(name = "ProductSearchCondition", description = "상품 검색 조건")
public record ProductSearchCondition(

        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "상품명 부분 일치", example = "골드바")
        String keyword,

        @Schema(description = "금속 종류", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "가격 방식", example = "MARKET_PLUS")
        PriceType priceType,

        @Schema(description = "판매 상태 (관리자 전용)", example = "ON_SALE")
        ProductStatus status,

        @Schema(description = "활성 여부 (관리자 전용)", example = "true")
        Boolean active
) {

    /** 공개 조회용 — 노출 가능한 상품만 보이도록 조건을 고정한다. */
    public ProductSearchCondition forPublic() {
        return new ProductSearchCondition(categoryId, keyword, metalType, null, null, Boolean.TRUE);
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
