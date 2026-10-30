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

        @Schema(description = "상세 상단 갤러리 이미지 키. 첫 장이 thumbnailKey 와 같다")
        List<String> galleryKeys,

        /**
         * 상품 설명 아래에 전체 폭으로 쌓는 이미지.
         *
         * <p>갤러리와 나눠 보내는 이유 — 이 자리에 올라오는 것은 860×9531 처럼 세로로 긴
         * 상세페이지다. 갤러리 썸네일에 넣으면 맨 위 한 조각만 보인다.
         */
        @Schema(description = "상품 설명 아래에 전체 폭으로 쌓는 상세페이지 이미지 키")
        List<String> detailKeys,

        @Schema(description = "연동 금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "중량(g)", example = "3.7500")
        BigDecimal weightG,

        @Schema(description = "순도 (표시 전용)", example = "0.9999")
        BigDecimal purity,

        @Schema(description = "판매 상태", example = "ON_SALE")
        ProductStatus status,

        /**
         * 주문을 받아 개별 생산하는 상품인가 (V33).
         *
         * <p>화면이 이 값으로 배송기간과 청약철회 안내를 갈라 적는다. 두 안내는 함께
         * 움직여야 한다 -- 제작 기간만 알리고 철회 제한을 빼면 제한이 성립하지 않고,
         * 철회 제한만 알리고 기간을 빼면 PG 심사에서 걸린다.
         */
        @Schema(description = "주문제작 상품인가. true 면 제작 기간과 청약철회 제한을 함께 고지한다",
                example = "false")
        boolean madeToOrder,

        @Schema(description = "옵션 목록 (재고 수량은 비공개)")
        List<ProductOptionResponse> options,

        @Schema(description = "옵션별 가격")
        List<PublicPriceQuoteResponse> prices
) {

    public static ProductDetailResponse of(Product product,
                                           List<String> galleryKeys,
                                           List<String> detailKeys,
                                           List<ProductOptionResponse> options,
                                           List<PublicPriceQuoteResponse> prices) {
        return new ProductDetailResponse(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getThumbnailKey(),
                galleryKeys,
                detailKeys,
                product.getMetalType(),
                product.getWeightG(),
                product.getPurity(),
                product.getStatus(),
                product.isMadeToOrder(),
                options,
                prices);
    }
}
