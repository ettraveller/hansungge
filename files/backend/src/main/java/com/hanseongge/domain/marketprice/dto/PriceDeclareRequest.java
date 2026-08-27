package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 관리자 수동 고시 요청 (개발계획 §12.3).
 *
 * <p>입력값은 <b>최종가가 아니라 원본가</b>다. 보정은 정책이 적용한다. 관리자가 최종가를
 * 바로 넣는 경로를 두면 원본 레이어가 비고, 그러면 "원본이 얼마였나"가 사라진다.
 * 최종가를 강제하려면 정책을 {@code MANUAL} + 지정가로 설정한다.
 *
 * <p>{@code changeReason} 은 필수다 — DB 의 NOT NULL 이자 감사 요건이다 (설계서 §6.3).
 */
@Schema(name = "PriceDeclareRequest", description = "관리자 수동 시세 고시 요청")
public record PriceDeclareRequest(

        @Schema(description = "금속", example = "GOLD_24K", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "금속을 선택해 주세요.")
        MetalType metalType,

        @Schema(description = "매입/판매", example = "SELL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "매입/판매를 선택해 주세요.")
        PriceSide priceSide,

        @Schema(description = "원본 시세 (KRW/1g). 돈 단위가 아니다",
                example = "200000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "시세를 입력해 주세요.")
        @DecimalMin(value = "0.01", message = "시세는 0보다 커야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "시세 형식이 올바르지 않습니다.")
        BigDecimal pricePerGram,

        @Schema(description = "고시 사유", example = "관리자 수동 고시",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "고시 사유를 입력해 주세요.")
        @Size(max = 300)
        String changeReason
) {
}
