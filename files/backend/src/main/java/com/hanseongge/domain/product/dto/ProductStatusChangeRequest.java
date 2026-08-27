package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 판매 상태 변경 요청.
 *
 * <p>사유가 필수다. 판매 중단은 매출에 직결되는 조치이고, 나중에 반드시 "왜 내렸나"를
 * 되묻게 된다.
 */
@Schema(name = "ProductStatusChangeRequest", description = "상품 판매 상태 변경 요청")
public record ProductStatusChangeRequest(

        @Schema(description = "변경할 상태", example = "ON_SALE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "상태를 선택해 주세요.")
        ProductStatus status,

        @Schema(description = "노출 여부. false 면 상태와 무관하게 모든 공개 조회에서 제외된다",
                example = "true")
        Boolean active,

        @Schema(description = "변경 사유", example = "재고 입고 완료, 판매 시작",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "변경 사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
