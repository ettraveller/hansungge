package com.hanseongge.domain.pricing.dto;

import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.pricing.service.PriceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 공개 가격 응답.
 *
 * <h2>관리자 응답과 다른 점</h2>
 * <ol>
 *   <li><b>가격이 어떻게 나왔는지를 내보내지 않는다.</b> 아래 참조</li>
 *   <li>가격을 표시할 수 없는 상태면 금액이 전부 {@code null} 이고
 *       {@link #displayText} 가 "시세문의"다 — <b>0 이 아니다</b></li>
 * </ol>
 *
 * <h2>내역과 시세를 빼는 이유 (2026-10-30)</h2>
 * 전에는 {@code components}(시세×중량, 공임)와 {@code marketPricePerGram} 을 담아
 * 상품 상세가 "왜 이 금액인가"를 보여줬다. 금액의 근거를 보이면 신뢰가 오른다고 봤다.
 *
 * <p>그런데 그 내역이 <b>공임을 그대로 공개한다.</b> 공임은 상품마다 다르게 매기는
 * 값이라 그대로 내걸면 상품끼리 비교당하고, 경쟁사가 마진을 그대로 읽는다.
 *
 * <p>⚠️ <b>화면에서 지우는 것으로는 부족하다.</b> 응답에 남아 있으면 개발자도구를
 * 열거나 API 를 직접 부르면 보인다 — 숨긴 것이 아니라 덜 눈에 띄게 한 것뿐이다.
 * 그래서 필드 자체를 없앴다. 관리자용 {@link PriceQuoteResponse} 는 그대로 담으므로
 * 가격이 어떻게 계산됐는지는 관리자 화면에서 확인한다.
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

        /*
         * components 와 marketPricePerGram 이 여기 없다. 공임과 시세 기준을 그대로
         * 공개하게 되기 때문이다 (위 설명). 관리자는 PriceQuoteResponse 로 본다.
         *
         * priceQuotedAt 은 남긴다 — "언제 기준 금액인가"는 시세 상품에서 고객이
         * 알아야 하는 정보이고, 그 자체로는 마진을 드러내지 않는다.
         */

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
                hasPrice ? quote.priceQuotedAt() : null,
                quote.unavailableReason());
    }
}
