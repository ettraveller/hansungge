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
         * <p>화면이 이 값으로 <b>구분과 청약철회 안내</b>를 갈라 적는다. 배송기간은
         * 2026-11-05(V41)부터 여기서 갈리지 않는다 -- {@link #deliveryPeriod} 가
         * 상품마다 들고 있다.
         */
        @Schema(description = "주문제작 상품인가. true 면 청약철회 제한을 함께 고지한다",
                example = "false")
        boolean madeToOrder,

        /**
         * 이 상품의 배송기간 문구 (V41). null 이면 화면이 기본 문구를 쓴다
         * (「상품별 상이 · 상세페이지 참조」).
         *
         * <p>⚠️ 비어 있는 상품은 <b>상세 이미지에 기간이 적혀 있어야 한다.</b> PG 심사가
         * 상품페이지 내 기재를 요구하고, 전자상거래법 §13② 의 공급시기 고지다.
         */
        @Schema(description = "배송기간 문구. null 이면 화면이 기본 문구를 쓴다",
                example = "결제 확인 후 영업일 7일 이내")
        String deliveryPeriod,

        @Schema(description = "택배사. null 이면 화면이 「우체국택배」를 쓴다", example = "우체국택배")
        String courier,

        /**
         * 카드·간편결제로 살 수 있는 상품인가. false 면 <b>무통장입금 전용</b>이다.
         *
         * <p>장바구니에 담기 <b>전에</b> 알려야 한다. 주문서까지 가서야 "이 상품은
         * 계좌로만 됩니다"를 보면, 카드로 사려던 사람은 거기서 멈추고 돌아간다.
         *
         * <p>판정은 상품이 스스로 한다 — 금종과 가격 방식에서 파생되고 운영자가 만질
         * 수 없다 ({@link com.hanseongge.domain.product.entity.Product#isCardPayable()}).
         */
        @Schema(description = "카드·간편결제 가능. false 면 무통장입금 전용", example = "true")
        boolean cardPayable,

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
                product.getDeliveryPeriod(),
                product.getCourier(),
                product.isCardPayable(),
                options,
                prices);
    }
}
