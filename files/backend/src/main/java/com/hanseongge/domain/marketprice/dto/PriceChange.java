package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 직전 고시 대비 변동폭.
 *
 * <h2>왜 백엔드가 계산하는가</h2>
 * 프론트엔드가 현재가와 이력을 받아 빼면 될 것 같지만, 그러면 <b>화면마다 다른 "직전"</b>이
 * 생긴다. 시세표는 마지막 고시를, 차트는 30일 전 값을, 상세는 또 다른 값을 기준으로 잡게
 * 되고 같은 상품에 서로 다른 변동률이 표시된다. 기준을 한 곳에 둔다 (설계서 원칙 P1).
 *
 * <h2>표시 전용이다</h2>
 * 이 값은 어떤 주문 금액에도 들어가지 않는다. 판매가는 {@code pricePerGram} 하나로만
 * 계산되고, 여기 담긴 수치는 고객에게 "얼마나 움직였는지"를 알리는 용도다.
 *
 * @param previousPricePerGram 직전 최종 시세(원/g)
 * @param changePerGram        변동액(원/g). 하락이면 음수
 * @param changePerDon         변동액(원/돈). 시세표가 1돈 기준으로 표시한다
 * @param changePercent        변동률(%). 소수 둘째 자리 반올림. 하락이면 음수
 */
@Schema(name = "PriceChange", description = "직전 고시 대비 변동폭 (표시 전용)")
public record PriceChange(

        @Schema(description = "직전 최종 시세 (원/g)", example = "216000.00")
        BigDecimal previousPricePerGram,

        @Schema(description = "변동액 (원/g). 하락이면 음수", example = "-6933.33")
        BigDecimal changePerGram,

        @Schema(description = "변동액 (원/돈). 하락이면 음수", example = "-26000.00")
        BigDecimal changePerDon,

        @Schema(description = "변동률 (%). 하락이면 음수", example = "-3.00")
        BigDecimal changePercent
) {

    /** 변동률 소수 자릿수. 화면이 "-3.00%" 로 그린다. */
    private static final int PERCENT_SCALE = 2;

    /**
     * 현재가와 직전 행으로 변동폭을 만든다.
     *
     * <p>직전 행이 없거나(최초 고시) 직전가가 0 이하이면 {@code null} 을 돌려준다.
     * 0 으로 나누면 터지고, "최초 고시"는 변동이 없는 게 아니라 <b>비교 대상이 없는 것</b>이라
     * 0% 로 표시하면 거짓말이 된다.
     */
    public static PriceChange of(BigDecimal currentPerGram, MarketPriceEffective previous) {
        if (currentPerGram == null || previous == null) {
            return null;
        }
        BigDecimal prev = previous.getPricePerGram();
        if (prev == null || prev.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal diffPerGram = currentPerGram.subtract(prev);
        BigDecimal percent = diffPerGram
                .multiply(BigDecimal.valueOf(100))
                .divide(prev, PERCENT_SCALE, RoundingMode.HALF_UP);

        return new PriceChange(
                prev,
                diffPerGram,
                diffPerGram.multiply(MetalType.GRAMS_PER_DON),
                percent);
    }
}
