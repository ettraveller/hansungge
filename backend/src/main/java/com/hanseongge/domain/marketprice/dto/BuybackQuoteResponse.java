package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 매입 예상 금액 — 고객이 "내 금 팔면 얼마인가"를 물을 때의 답.
 *
 * <h2>금액을 서버가 낸다</h2>
 * 화면이 시세 × 중량을 곱하지 않는다 (설계서 원칙 P1). 곱셈 한 줄이라 사소해 보이지만,
 * 반올림 자리나 환산 비율이 한쪽만 바뀌는 날 <b>화면의 숫자와 가게가 쳐 주는 값이
 * 갈라진다.</b> 그 차이를 들고 오는 고객을 설득할 방법은 없다.
 *
 * <h2>0 원을 돌려주지 않는다</h2>
 * 고시된 적 없는 금속(지금은 은·백금)은 {@code available=false} 로 내려보내고 금액을
 * 비운다. 0 원을 적으면 "안 쳐 준다"로 읽힌다 (설계서 원칙 P6).
 */
@Schema(name = "BuybackQuoteResponse", description = "매입 예상 금액")
public record BuybackQuoteResponse(

        @Schema(description = "금속", example = "GOLD_24K") MetalType metalType,
        @Schema(description = "금속 표시명", example = "순금 24K") String metalName,

        @Schema(description = "매입가를 낼 수 있는가. false 면 아직 고시 전이라 '시세문의'")
        boolean available,

        @Schema(description = "입력 중량(g)", example = "7.5000") BigDecimal weightG,
        @Schema(description = "입력 중량(돈)", example = "2.0000") BigDecimal weightDon,

        @Schema(description = "적용 매입가(원/g). 고시 전이면 null") BigDecimal pricePerGram,
        @Schema(description = "적용 매입가(원/돈). 고시 전이면 null") BigDecimal pricePerDon,

        /**
         * 매입 예상 금액(원). 고시 전이면 null.
         *
         * <p><b>확정 금액이 아니다.</b> 실제 지급액은 저울에 올린 중량과 순도 검사로
         * 정해진다. 화면이 이 값을 「예상」이라고 분명히 적어야 한다.
         */
        @Schema(description = "매입 예상 금액(원). 고시 전이면 null", example = "1282000")
        BigDecimal amount
) {
}
