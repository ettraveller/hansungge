package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.pricing.dto.PublicPriceQuoteResponse;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 공개 상품 목록 한 줄.
 *
 * <p>가격은 <b>대표가(최저 옵션가)</b>다. 옵션마다 중량이 다르면 가격도 다르므로 목록에서는
 * "○○원~" 으로 표시한다. 시세가 없거나 노후면 {@code price} 가 null 이고
 * {@code displayText} 가 "시세문의"다 — <b>0 이 아니다.</b>
 *
 * <p>공임·중량 같은 원가 재료는 담지 않는다. 목록은 스크래핑되기 가장 쉬운 화면이다.
 */
@Schema(name = "ProductSummaryResponse", description = "상품 목록 항목 (공개)")
public record ProductSummaryResponse(

        @Schema(description = "상품 ID", example = "1")
        Long id,

        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "상품명", example = "순금 골드바 3.75g (1돈)")
        String name,

        @Schema(description = "대표 이미지 Object Storage 키")
        String thumbnailKey,

        @Schema(description = "연동 금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "중량(g)", example = "3.7500")
        BigDecimal weightG,

        @Schema(description = "판매 상태", example = "ON_SALE")
        ProductStatus status,

        @Schema(description = "대표가(원). 표시 불가면 null", example = "837500.00")
        BigDecimal price,

        @Schema(description = "화면 표시 문구", example = "837,500")
        String priceText,

        @Schema(description = "옵션이 여럿이라 '~' 표기가 필요한가", example = "true")
        boolean priceFrom,

        @Schema(description = "주문 가능 여부", example = "true")
        boolean orderable
) {

    public static ProductSummaryResponse of(Product product, PriceQuote quote, boolean multipleOptions) {
        boolean hasPrice = quote != null && quote.status().hasPrice() && quote.finalUnitPrice() != null;

        return new ProductSummaryResponse(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getThumbnailKey(),
                product.getMetalType(),
                product.getWeightG(),
                product.getStatus(),
                hasPrice ? quote.finalUnitPrice().value() : null,
                hasPrice ? quote.finalUnitPrice().value().toPlainString()
                         : PublicPriceQuoteResponse.INQUIRY_TEXT,
                hasPrice && multipleOptions,
                quote != null && quote.status().isOrderable());
    }
}
