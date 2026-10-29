package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.ProductPricing;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 등록 전 가격 미리보기 요청.
 *
 * <p>{@link ProductRequest} 를 그대로 쓰지 않는 이유 — 거기에는 카테고리·상품명이
 * 필수다. 가격 계산에 쓰이지 않는 값을 채워야 미리보기를 볼 수 있게 되면, 폼을
 * 반만 채운 상태에서 "이 중량·공임이면 얼마인가"를 확인할 수 없다.
 *
 * <p>여기 담긴 값으로 <b>저장하지 않는</b> 상품을 만들어 실제 계산식에 통과시킨다
 * (ProductService.previewDraftPrice).
 */
@Schema(name = "ProductDraftPriceRequest", description = "등록 전 가격 미리보기 (저장하지 않는다)")
public record ProductDraftPriceRequest(

        @Schema(description = "가격 방식", example = "MARKET_PLUS",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "가격 방식을 선택해 주세요.")
        PriceType priceType,

        @Schema(description = "연동 금속. 시세 연동 방식이면 필수", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "중량(g). 시세 연동 방식이면 필수", example = "3.7500")
        @Digits(integer = 8, fraction = 4, message = "중량 형식이 올바르지 않습니다.")
        BigDecimal weightG,

        /** 표시 전용. 계산에 들어가지 않는다 (개발계획 §9.2). */
        @Schema(description = "순도. 표시 전용", example = "0.9999")
        @Digits(integer = 1, fraction = 4, message = "순도 형식이 올바르지 않습니다.")
        BigDecimal purity,

        @Schema(description = "공임", example = "50000")
        @Digits(integer = 13, fraction = 2, message = "공임 형식이 올바르지 않습니다.")
        BigDecimal laborCost,

        @Schema(description = "배수. MARKET_MULTIPLIER 에서만 쓴다", example = "1.0000")
        @Digits(integer = 5, fraction = 4, message = "배수 형식이 올바르지 않습니다.")
        BigDecimal multiplier,

        @Schema(description = "고정가. FIXED/MANUAL 이면 필수", example = "250000")
        @Digits(integer = 13, fraction = 2, message = "판매가 형식이 올바르지 않습니다.")
        BigDecimal fixedPrice,

        @Schema(description = "옵션 추가공임. 기본 옵션 기준으로 본다", example = "0")
        @Digits(integer = 13, fraction = 2, message = "추가공임 형식이 올바르지 않습니다.")
        BigDecimal additionalLaborCost
) {

    /** {@link ProductPricing} 의 생성자가 방식별 필수 필드를 함께 검증한다. */
    public ProductPricing toPricing() {
        return new ProductPricing(metalType, priceType, weightG, purity,
                laborCost, multiplier, fixedPrice);
    }
}
