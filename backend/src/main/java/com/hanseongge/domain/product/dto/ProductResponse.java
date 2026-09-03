package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 관리자 상품 응답 — <b>가격을 만드는 재료</b>가 그대로 나온다.
 *
 * <p>판매가 필드가 없는 것이 정상이다. 가격은 저장되지 않고 계산되며, 확인은
 * {@code GET /api/admin/products/{id}/price-preview} 로 한다.
 */
@Schema(name = "ProductResponse", description = "상품 (관리자)")
public record ProductResponse(

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

        @Schema(description = "연동 금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "가격 방식", example = "MARKET_PLUS")
        PriceType priceType,

        @Schema(description = "가격 방식 표시명", example = "시세 연동 + 옵션공임")
        String priceTypeName,

        @Schema(description = "중량(g)", example = "3.7500")
        BigDecimal weightG,

        @Schema(description = "순도 (표시 전용, 계산 미사용)", example = "0.9999")
        BigDecimal purity,

        @Schema(description = "공임(원)", example = "50000.00")
        BigDecimal laborCost,

        @Schema(description = "배수", example = "1.0000")
        BigDecimal multiplier,

        @Schema(description = "고정 판매가(원). FIXED/MANUAL 전용")
        BigDecimal fixedPrice,

        @Schema(description = "판매 상태", example = "ON_SALE")
        ProductStatus status,

        @Schema(description = "활성 여부", example = "true")
        boolean active,

        @Schema(description = "등록자", example = "admin")
        String createdBy,

        @Schema(description = "등록 시각")
        OffsetDateTime createdAt,

        @Schema(description = "마지막 수정자", example = "admin")
        String updatedBy,

        @Schema(description = "마지막 수정 시각")
        OffsetDateTime updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getThumbnailKey(),
                product.getMetalType(),
                product.getPriceType(),
                product.getPriceType().displayName(),
                product.getWeightG(),
                product.getPurity(),
                product.getLaborCost(),
                product.getMultiplier(),
                product.getFixedPrice(),
                product.getStatus(),
                product.isActive(),
                product.getCreatedBy(),
                product.getCreatedAt(),
                product.getUpdatedBy(),
                product.getUpdatedAt());
    }
}
