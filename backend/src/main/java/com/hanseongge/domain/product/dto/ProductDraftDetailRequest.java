package com.hanseongge.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 등록 전 상품 상세 미리보기 요청.
 *
 * <p>가격 재료는 {@link ProductDraftPriceRequest} 를 그대로 품는다. 둘로 나눠 적으면
 * 한쪽만 고치는 날 "가격 미리보기와 상세 미리보기가 다른 금액"이 된다.
 */
@Schema(name = "ProductDraftDetailRequest", description = "등록 전 상세 미리보기 (저장하지 않는다)")
public record ProductDraftDetailRequest(

        @Schema(description = "상품명. 비어 있어도 된다 — 아직 채우는 중일 수 있다")
        @Size(max = 200)
        String name,

        @Schema(description = "상품 설명")
        String description,

        @Schema(description = "대표 이미지 키 (순서대로)")
        List<@Size(max = 300) String> galleryKeys,

        @Schema(description = "상세 이미지 키 (순서대로)")
        List<@Size(max = 300) String> detailKeys,

        @Schema(description = "주문제작 상품인가. 상세의 배송기간·청약철회 안내가 달라진다",
                example = "false")
        boolean madeToOrder,

        @Schema(description = "과세 상품인가. 미리보기 금액의 부가세 표시가 갈린다", example = "false")
        boolean vatTaxable,

        @Schema(description = "가격 재료", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "가격 재료를 보내 주세요.")
        @Valid
        ProductDraftPriceRequest pricing
) {

    public ProductDraftDetailRequest {
        // null 을 그대로 두면 화면이 매번 널 검사를 해야 하고, 한 번 빠뜨리면 미리보기가
        // 통째로 빈 화면이 된다.
        galleryKeys = galleryKeys == null ? List.of() : List.copyOf(galleryKeys);
        detailKeys = detailKeys == null ? List.of() : List.copyOf(detailKeys);
        name = name == null ? "" : name;
    }
}
