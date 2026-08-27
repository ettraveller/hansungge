package com.hanseongge.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 담기.
 *
 * <p><b>금액 필드가 없다.</b> 프론트엔드가 보낸 가격은 어디에서도 읽지 않는다 —
 * 담은 시점 단가는 서버가 그 순간 계산해 기록한다 (원칙 P1).
 */
@Schema(name = "AddCartItemRequest", description = "장바구니 담기")
public record AddCartItemRequest(

        @Schema(description = "상품 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "상품을 선택해 주세요.")
        Long productId,

        @Schema(description = "옵션 ID. 비우면 기본 옵션을 쓴다", example = "3")
        Long optionId,

        @Schema(description = "수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "수량을 입력해 주세요.")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
        @Max(value = 99, message = "한 번에 99개까지 담을 수 있습니다.")
        Integer quantity
) {}
