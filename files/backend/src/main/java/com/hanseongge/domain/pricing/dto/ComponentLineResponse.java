package com.hanseongge.domain.pricing.dto;

import com.hanseongge.domain.pricing.service.CalcMethod;
import com.hanseongge.domain.pricing.service.ComponentLine;
import com.hanseongge.domain.pricing.service.ComponentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 가격 내역 한 줄.
 *
 * <p>계산 근거를 함께 담아 상세페이지가 "210,000원/g × 3.75g = 787,500원" 을 그릴 수
 * 있게 한다. 금액만 내려주면 프론트엔드가 근거를 다시 계산해야 하고, 그 순간
 * <b>가격을 계산하는 곳이 두 군데</b>가 된다 (코딩규칙 P1 위반).
 */
@Schema(name = "ComponentLineResponse", description = "가격 구성요소")
public record ComponentLineResponse(

        @Schema(description = "표시 순서", example = "10")
        int sort,

        @Schema(description = "구성요소 종류", example = "METAL_BASE")
        ComponentType type,

        @Schema(description = "종류 표시명", example = "금액")
        String typeName,

        @Schema(description = "고객 노출 문구", example = "순금 24K 시세")
        String label,

        @Schema(description = "계산 방식", example = "PER_GRAM_MARKET")
        CalcMethod method,

        @Schema(description = "단가 (원/g). PER_GRAM_* 방식만", example = "210000.00")
        BigDecimal unitPricePerGram,

        @Schema(description = "중량 (g). PER_GRAM_* 방식만", example = "3.7500")
        BigDecimal weightG,

        @Schema(description = "비율. PERCENT_* / MULTIPLIER_* 방식만", example = "0.1500")
        BigDecimal rate,

        @Schema(description = "금액 (원)", example = "787500.00")
        BigDecimal amount
) {

    public static ComponentLineResponse from(ComponentLine line) {
        return new ComponentLineResponse(
                line.sort(),
                line.type(),
                line.type().displayName(),
                line.label(),
                line.method(),
                line.unitPricePerGram() == null ? null : line.unitPricePerGram().value(),
                line.weight() == null ? null : line.weight().value(),
                line.rate(),
                line.amount().value());
    }
}
