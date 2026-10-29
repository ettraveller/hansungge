package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 살 때·팔 때를 함께 고시하는 요청.
 *
 * <p>{@code priceSide} 가 없다. 한쪽만 지정할 수 없게 하는 것이 이 DTO 의 목적이다 —
 * 한쪽씩 고시하면 그 사이에 "살 때는 있고 팔 때는 없는" 시세표가 화면에 걸린다.
 *
 * <h2>단위는 돈(3.75g)이다</h2>
 * 이 업종의 시세는 돈 단위로 돌아다닌다. g 단위로 받으면 관리자가 매번 3.75 로 나눠
 * 넣어야 하고, 그 나눗셈을 사람이 하는 동안 자리를 하나 흘리면 판매가가 그대로 어긋난다.
 *
 * <p>저장은 계속 원/g 이다. 환산은 {@link #sellPricePerGram()} / {@link #buyPricePerGram()}
 * 이 하고, 실제 나눗셈은 {@link MetalType#perGramFromDon} 한 곳에만 있다.
 */
@Schema(name = "PriceDeclarePairRequest", description = "살 때·팔 때 동시 고시 (단위: 원/돈)")
public record PriceDeclarePairRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "고객이 살 때(판매) 시세. <b>KRW / 1돈(3.75g)</b>",
                example = "828000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "살 때 시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "살 때 시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "살 때 시세 형식이 올바르지 않습니다.")
        BigDecimal sellPricePerDon,

        @Schema(description = "고객이 팔 때(매입) 시세. <b>KRW / 1돈(3.75g)</b>",
                example = "705000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "팔 때 시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "팔 때 시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "팔 때 시세 형식이 올바르지 않습니다.")
        BigDecimal buyPricePerDon,

        @Schema(description = "고시 사유", example = "오전 고시",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "고시 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason,

        @Schema(description = "직전 대비 확인 기준을 넘는 값이 있다는 것을 확인했는가. "
                + "미리보기에서 needsConfirm 이 true 였다면 true 로 보내야 발행된다", example = "false")
        Boolean confirmLargeChange
) {

    /** 저장·검증에 쓰는 원/g 값. */
    public BigDecimal sellPricePerGram() {
        return MetalType.perGramFromDon(sellPricePerDon);
    }

    /** 저장·검증에 쓰는 원/g 값. */
    public BigDecimal buyPricePerGram() {
        return MetalType.perGramFromDon(buyPricePerDon);
    }
}
