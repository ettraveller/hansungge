package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.ProductPricing;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 상품 등록·수정 요청.
 *
 * <p>{@code price_type} 별 필수값(중량·금속·고정가)은 Bean Validation 으로 표현할 수 없어
 * {@link ProductPricing} 이 검사한다 — 한 필드의 필수 여부가 다른 필드 값에 달려 있기 때문이다.
 *
 * <p><b>판매가를 받지 않는다.</b> 받는 것은 가격을 만드는 재료뿐이고, 판매가는 조회 시점에
 * 계산된다 (개발계획 §9.1).
 */
@Schema(name = "ProductRequest", description = "상품 등록/수정 요청")
public record ProductRequest(

        @Schema(description = "카테고리 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "카테고리를 선택해 주세요.")
        Long categoryId,

        @Schema(description = "상품명", example = "순금 골드바 3.75g (1돈)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "상품명을 입력해 주세요.")
        @Size(max = 200)
        String name,

        @Schema(description = "상품 설명 (HTML 허용)")
        String description,

        @Schema(description = "대표 이미지 Object Storage 키. URL 이 아니다",
                example = "products/2026/goldbar-375.jpg")
        @Size(max = 300)
        String thumbnailKey,

        @Schema(description = "연동 금속. 시세 연동 상품이면 필수", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "가격 방식", example = "MARKET_PLUS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "가격 방식을 선택해 주세요.")
        PriceType priceType,

        @Schema(description = "중량(g). 시세 연동 상품이면 필수", example = "3.75")
        @DecimalMin(value = "0.0001", message = "중량은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 4)
        BigDecimal weightG,

        @Schema(description = "순도. **표시 전용이며 계산에 쓰이지 않는다**", example = "0.9999")
        @Digits(integer = 2, fraction = 4)
        BigDecimal purity,

        @Schema(description = "공임/가공비(원)", example = "50000")
        @DecimalMin(value = "0", message = "공임은 0 이상이어야 합니다.")
        @Digits(integer = 13, fraction = 2)
        BigDecimal laborCost,

        @Schema(description = "배수. MARKET_MULTIPLIER 전용", example = "1.15")
        @Digits(integer = 4, fraction = 4)
        BigDecimal multiplier,

        @Schema(description = "고정 판매가(원). FIXED/MANUAL 전용", example = "12000")
        @Digits(integer = 13, fraction = 2)
        BigDecimal fixedPrice,

        @Schema(description = "기본 옵션 이름. 비우면 중량으로 자동 생성 (등록 시에만 사용)",
                example = "3.75g (1돈)")
        @Size(max = 100)
        String defaultOptionName,

        @Schema(description = "기본 옵션 초기 재고 (등록 시에만 사용)", example = "10")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer initialStock
) {

    /** 가격 재료로 변환. 여기서 {@code price_type} 별 정합성이 검사된다. */
    public ProductPricing toPricing() {
        return new ProductPricing(metalType, priceType, weightG, purity, laborCost, multiplier, fixedPrice);
    }
}
