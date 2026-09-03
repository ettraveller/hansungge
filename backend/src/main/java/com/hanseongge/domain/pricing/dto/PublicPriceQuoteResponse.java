package com.hanseongge.domain.pricing.dto;

import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.pricing.service.PriceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 공개 가격 응답.
 *
 * <h2>관리자 응답과 다른 점</h2>
 * <ol>
 *   <li><b>원본 시세·보정액을 내보내지 않는다.</b> 회사의 마진 정책이다</li>
 *   <li><b>{@code visible=true} 내역만</b> 담는다</li>
 *   <li>가격을 표시할 수 없는 상태면 금액이 전부 {@code null} 이고
 *       {@link #displayText} 가 "시세문의"다 — <b>0 이 아니다</b></li>
 * </ol>
 */
@Schema(name = "PublicPriceQuoteResponse", description = "상품 가격 (공개)")
public record PublicPriceQuoteResponse(

        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "옵션 ID", example = "1")
        Long optionId,

        @Schema(description = "산출 상태", example = "AVAILABLE")
        PriceStatus status,

        @Schema(description = "주문 가능 여부", example = "true")
        boolean orderable,

        @Schema(description = "최종 판매 단가(원). 표시 불가면 null", example = "837500.00")
        BigDecimal price,

        @Schema(description = "화면 표시 문구", example = "837,500")
        String displayText,

        @Schema(description = "가격 내역 (노출 항목만)")
        List<ComponentLineResponse> components,

        @Schema(description = "적용된 1g 시세(원). 시세 미연동 상품이면 null", example = "210000.00")
        BigDecimal marketPricePerGram,

        @Schema(description = "시세 기준 시각")
        OffsetDateTime priceQuotedAt,

        @Schema(description = "안내 문구. 정상이면 null", example = "시세문의 후 주문해 주세요.")
        String notice
) {

    /** 가격을 표시할 수 없을 때의 문구 (개발계획 §12.4). */
    public static final String INQUIRY_TEXT = "시세문의";

    public static PublicPriceQuoteResponse from(PriceQuote quote) {
        boolean hasPrice = quote.status().hasPrice() && quote.finalUnitPrice() != null;

        return new PublicPriceQuoteResponse(
                quote.productId(),
                quote.optionId(),
                quote.status(),
                quote.status().isOrderable(),
                hasPrice ? quote.finalUnitPrice().value() : null,
                hasPrice ? quote.finalUnitPrice().value().toPlainString() : INQUIRY_TEXT,
                hasPrice ? quote.visibleComponents().stream().map(ComponentLineResponse::from).toList()
                         : List.of(),
                hasPrice && quote.appliedMarketPrice() != null
                        ? quote.appliedMarketPrice().value() : null,
                hasPrice ? quote.priceQuotedAt() : null,
                quote.unavailableReason());
    }
}
