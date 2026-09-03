package com.hanseongge.domain.pricing.dto;

import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.pricing.service.PriceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자용 가격 산출 결과 — <b>원본 시세와 보정액까지 전부</b>.
 *
 * <p>공개 응답({@link PublicPriceQuoteResponse})과 나눈 이유는 마진 노출이다.
 * 여기에는 원본 시세·보정액·미확정 정책 여부가 들어간다. 운영자는 "지금 이 가격이
 * 어떤 시세에 얼마를 보정해서 나왔는지"와 "정책이 아직 임시값인지"를 봐야 한다.
 */
@Schema(name = "PriceQuoteResponse", description = "가격 산출 결과 (관리자)")
public record PriceQuoteResponse(

        @Schema(description = "산출 상태", example = "AVAILABLE")
        PriceStatus status,

        @Schema(description = "주문 가능 여부", example = "true")
        boolean orderable,

        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "옵션 ID", example = "1")
        Long optionId,

        @Schema(description = "적용된 최종 시세 (원/g)", example = "210000.00")
        BigDecimal appliedMarketPrice,

        @Schema(description = "원본 시세 (원/g). 관리자 전용", example = "200000.00")
        BigDecimal rawMarketPrice,

        @Schema(description = "시세 보정액. 관리자 전용", example = "10000.00")
        BigDecimal marketAdjustment,

        @Schema(description = "근거 market_prices_effective.id", example = "101")
        Long effectivePriceId,

        @Schema(description = "시세 기준 시각")
        OffsetDateTime priceQuotedAt,

        @Schema(description = "가격 내역 (숨김 항목 포함)")
        List<ComponentLineResponse> components,

        @Schema(description = "구성요소 합계", example = "837500.00")
        BigDecimal subtotal,

        @Schema(description = "부가세 정책", example = "NONE")
        String vatMode,

        @Schema(description = "부가세 정책이 A-1 확정 전 임시값인지", example = "true")
        boolean vatPolicyTemporary,

        @Schema(description = "부가세액", example = "0.00")
        BigDecimal vat,

        @Schema(description = "반올림 정책", example = "CEIL_100")
        String roundingPolicy,

        @Schema(description = "반올림 정책이 A-2 확정 전 임시값인지", example = "false")
        boolean roundingPolicyTemporary,

        @Schema(description = "반올림으로 조정된 금액 (최종 - 소계 - 세액)", example = "0.00")
        BigDecimal roundingDelta,

        @Schema(description = "최종 판매 단가", example = "837500.00")
        BigDecimal finalUnitPrice,

        @Schema(description = "판매 불가 사유. 정상이면 null")
        String unavailableReason
) {

    public static PriceQuoteResponse from(PriceQuote quote) {
        return new PriceQuoteResponse(
                quote.status(),
                quote.status().isOrderable(),
                quote.productId(),
                quote.optionId(),
                quote.appliedMarketPrice() == null ? null : quote.appliedMarketPrice().value(),
                quote.rawMarketPrice() == null ? null : quote.rawMarketPrice().value(),
                quote.marketAdjustment(),
                quote.effectivePriceId(),
                quote.priceQuotedAt(),
                quote.components().stream().map(ComponentLineResponse::from).toList(),
                quote.subtotal() == null ? null : quote.subtotal().value(),
                quote.vatMode() == null ? null : quote.vatMode().name(),
                quote.vatPolicyTemporary(),
                quote.vat() == null ? null : quote.vat().value(),
                quote.roundingPolicy() == null ? null : quote.roundingPolicy().name(),
                quote.roundingPolicyTemporary(),
                quote.roundingDelta() == null ? null : quote.roundingDelta().value(),
                quote.finalUnitPrice() == null ? null : quote.finalUnitPrice().value(),
                quote.unavailableReason());
    }
}
