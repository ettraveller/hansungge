package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.pricing.dto.PublicPriceQuoteResponse;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 공개 상품 상세.
 *
 * <p>옵션별 가격을 함께 담는다. 옵션을 고를 때마다 서버를 다시 부르지 않아도 되고,
 * 무엇보다 <b>프론트엔드가 가격을 계산할 필요가 없다</b> — 계산하는 곳이 둘이 되는 순간
 * 두 값이 어긋나기 시작한다 (코딩규칙 P1).
 *
 * @param options 옵션 목록. 항상 1개 이상이다 (등록 시 기본 옵션이 자동 생성된다)
 * @param prices  옵션과 같은 순서의 가격. 시세문의 상태면 금액이 null 이다
 */
@Schema(name = "ProductDetailResponse", description = "상품 상세 (공개)")
public record ProductDetailResponse(

        @Schema(description = "상품 ID", example = "1")
        Long id,

        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "상품명", example = "순금 골드바 3.75g (1돈)")
        String name,

        @Schema(description = "상품 설명")
        String description,

        @Schema(description = "대표 이미지 Object Storage 키")
        String thumbnailKey,

        @Schema(description = "추가 이미지 키 목록")
        List<String> imageKeys,

        @Schema(description = "연동 금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "중량(g)", example = "3.7500")
        BigDecimal weightG,

        @Schema(description = "순도 (표시 전용)", example = "0.9999")
        BigDecimal purity,

        @Schema(description = "판매 상태", example = "ON_SALE")
        ProductStatus status,

        @Schema(description = "옵션 목록 (재고 수량은 비공개)")
        List<ProductOptionResponse> options,

        @Schema(description = "옵션별 가격")
        List<PublicPriceQuoteResponse> prices
) {

    public static ProductDetailResponse of(Product product, List<String> imageKeys,
                                           List<ProductOptionResponse> options,
                                           List<PublicPriceQuoteResponse> prices) {
        return new ProductDetailResponse(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getThumbnailKey(),
                imageKeys,
                product.getMetalType(),
                product.getWeightG(),
                product.getPurity(),
                product.getStatus(),
                options,
                prices);
    }
}
