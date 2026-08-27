package com.hanseongge.domain.cart.dto;

import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.pricing.service.PriceStatus;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.cart.entity.CartItem;
import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 장바구니 한 줄.
 *
 * <p>금액은 <b>지금 다시 계산한 값</b>이다 (개발계획 §13.6). {@code priceAtAdd} 와 견줘
 * 달라졌으면 {@link #priceChanged} 가 켜지고 {@link #priceDelta} 에 차액이 담긴다 —
 * "담을 때보다 12,000원 올랐습니다"를 화면이 직접 계산하지 않게 하기 위해서다.
 *
 * <p>가격을 낼 수 없는 상태(시세 없음·노후)면 금액 자리가 전부 null 이고
 * {@link #priceText} 가 "시세문의"다. <b>0 이 아니다</b> — 0 은 화면에서 "0원"으로 그려진다.
 */
@Schema(name = "CartItemResponse", description = "장바구니 항목")
public record CartItemResponse(

        @Schema(description = "장바구니 항목 ID", example = "12") Long id,
        @Schema(description = "상품 ID", example = "3") Long productId,
        @Schema(description = "옵션 ID", example = "3") Long optionId,
        @Schema(description = "상품명") String productName,
        @Schema(description = "옵션명") String optionName,
        @Schema(description = "대표 이미지 Object Storage 키") String thumbnailKey,
        @Schema(description = "연동 금속") MetalType metalType,
        @Schema(description = "중량(g)") BigDecimal weightG,
        @Schema(description = "수량", example = "2") int quantity,

        @Schema(description = "현재 단가(원). 표시 불가면 null") BigDecimal unitPrice,
        @Schema(description = "현재 줄 합계(단가×수량). 표시 불가면 null") BigDecimal lineAmount,
        @Schema(description = "화면 표시 문구", example = "601,300") String priceText,

        @Schema(description = "담은 시점 단가") BigDecimal priceAtAdd,
        @Schema(description = "담은 뒤 단가가 바뀌었는가", example = "true") boolean priceChanged,
        @Schema(description = "단가 차액(현재 - 담을 때). 오르면 양수") BigDecimal priceDelta,

        @Schema(description = "지금 주문할 수 있는가", example = "true") boolean orderable,
        @Schema(description = "주문할 수 없는 이유. 가능하면 null") String unavailableReason,
        @Schema(description = "재고가 담은 수량보다 적은가", example = "false") boolean stockShortage,
        @Schema(description = "남은 재고. 재고 부족일 때만 채워진다") Integer availableStock
) {

    /** 시세를 확인할 수 없을 때의 문구 (설계서 §12.4). */
    public static final String INQUIRY_TEXT = "시세문의";

    public static CartItemResponse of(CartItem item, Product product, ProductOption option,
                                      PriceQuote quote, int currentStock) {
        boolean hasPrice = quote.status().hasPrice() && quote.finalUnitPrice() != null;
        BigDecimal unitPrice = hasPrice ? quote.finalUnitPrice().value() : null;
        BigDecimal lineAmount = hasPrice
                ? quote.finalUnitPrice().multiply(item.getQuantity()).value()
                : null;

        // 차액은 서버가 낸다. 화면에서 빼면 그 순간부터 표시 금액과 서버 금액이 갈라진다.
        BigDecimal delta = unitPrice == null ? null : unitPrice.subtract(item.getPriceAtAdd());
        boolean changed = delta != null && delta.signum() != 0;

        boolean shortage = currentStock < item.getQuantity();

        return new CartItemResponse(
                item.getId(),
                item.getProductId(),
                item.getOptionId(),
                product.getName(),
                option.getName(),
                product.getThumbnailKey(),
                product.getMetalType(),
                option.getWeightG() != null ? option.getWeightG() : product.getWeightG(),
                item.getQuantity(),
                unitPrice,
                lineAmount,
                hasPrice ? unitPrice.toPlainString() : INQUIRY_TEXT,
                item.getPriceAtAdd(),
                changed,
                delta,
                // 재고가 모자라면 가격이 나와도 주문할 수 없다.
                quote.status().isOrderable() && !shortage,
                reasonOf(quote.status(), shortage, quote.unavailableReason()),
                shortage,
                shortage ? currentStock : null);
    }

    private static String reasonOf(PriceStatus status, boolean shortage, String quoteReason) {
        if (shortage) {
            return "재고가 부족합니다. 수량을 줄이거나 삭제해 주세요.";
        }
        return switch (status) {
            case AVAILABLE -> null;
            case INQUIRY_ONLY -> quoteReason != null ? quoteReason
                    : "시세를 확인할 수 없어 주문할 수 없습니다.";
            case SOLD_OUT -> "품절된 상품입니다.";
            case NOT_FOR_SALE -> "현재 판매하지 않는 상품입니다.";
        };
    }
}
