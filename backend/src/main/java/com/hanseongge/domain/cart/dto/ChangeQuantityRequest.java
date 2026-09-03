package com.hanseongge.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ChangeQuantityRequest", description = "장바구니 수량 변경")
public record ChangeQuantityRequest(

        @Schema(description = "변경 후 수량 (절대값)", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "수량을 입력해 주세요.")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다. 빼려면 삭제해 주세요.")
        @Max(value = 99, message = "99개까지 담을 수 있습니다.")
        Integer quantity
) {}
