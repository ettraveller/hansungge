package com.hanseongge.domain.product.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.DeliveryNotice;
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

        @Schema(description = "상품 설명. 평문으로 표시된다 — 사진은 이미지 목록으로 올린다")
        String description,

        // ⚠️ thumbnailKey 를 받지 않는다. 대표이미지 목록(PUT /images?kind=GALLERY)의
        // 첫 장이 그 값이 된다 (V31). 여기서 함께 받으면 상품 정보를 저장할 때마다
        // 폼에 없는 값이 null 로 덮어써서 목록의 사진이 사라진다 — 사진을 지운 적이
        // 없는데 사라지므로 원인을 찾기 어렵다.

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

        /*
         * 기본값을 false 로 둔다. 이 필드를 보내지 않는 옛 요청이 조용히 "주문제작"으로
         * 저장되면, 재고가 있는 상품까지 청약철회가 제한된 것으로 고지되어 고객의
         * 권리를 근거 없이 깎는다. 빠뜨렸을 때 안전한 쪽이 false 다 (V33 참조).
         */
        @Schema(description = "주문을 받아 개별 생산하는 상품인가. true 면 상세에 제작 기간과 "
                + "청약철회 제한을 함께 고지한다 (전자상거래법 §17②①)", example = "false")
        boolean madeToOrder,

        /**
         * 과세 상품인가 (V38).
         *
         * <p>과세여도 <b>판매가는 바뀌지 않는다.</b> 표시가가 곧 받는 돈이고(부가세 포함),
         * 그 안에 얼마가 세금인지를 주문서·영수증에서 쪼개 보여 줄 뿐이다.
         *
         * <p>세율은 받지 않는다 — 법이 정하는 값이지 상품의 성질이 아니다. 상품마다
         * 숫자를 받으면 오타 하나가 그대로 금액이 된다. {@code VAT_RATE} 설정을 쓴다.
         *
         * <p>빠뜨렸을 때 안전한 쪽은 false(면세)다. 면세인 금지금을 과세로 적으면
         * 받지도 않은 세금을 받은 것처럼 영수증에 남는다.
         */
        @Schema(description = "과세 상품인가. true 면 판매가에 부가세가 포함된 것으로 보고 "
                + "공급가액/세액을 쪼갠다. 금액 자체는 바뀌지 않는다", example = "false")
        boolean vatTaxable,

        /**
         * 이 상품의 배송기간 문구 (V41).
         *
         * <p>비우면 상세 화면이 「상품별 상이 · 상세페이지 참조」를 쓴다. 숫자가 아니라
         * 문장을 받는다 — 「결제 확인 후 영업일 7일 이내」와 「결제일부터 최대 6주」는
         * 기준 시점부터 다르고, 주수만 받으면 그 차이를 화면이 지어내야 한다.
         *
         * <p>⚠️ 비워 두는 상품은 상세 이미지에 기간이 적혀 있어야 한다 (PG 심사 요건,
         * 전자상거래법 §13② 공급시기 고지).
         */
        @Schema(description = "배송기간 문구. 비우면 「상품별 상이 · 상세페이지 참조」로 표시된다",
                example = "결제 확인 후 영업일 7일 이내")
        @Size(max = 100, message = "배송기간은 100자 이내로 적어 주세요.")
        String deliveryPeriod,

        @Schema(description = "택배사. 비우면 「우체국택배」로 표시된다", example = "우체국택배")
        @Size(max = 40, message = "택배사는 40자 이내로 적어 주세요.")
        String courier,

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

    /** 배송 고지로 변환. 공백은 여기서 "안 정했다"(null)로 접힌다. */
    public DeliveryNotice toDeliveryNotice() {
        return DeliveryNotice.of(deliveryPeriod, courier);
    }
}
