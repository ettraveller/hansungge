package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.service.DerivationUpdate;
import io.swagger.v3.oas.annotations.media.Schema;

/** 고시 방식 변경 결과. */
@Schema(name = "DerivationUpdateResponse", description = "18K·14K 고시 방식 변경 결과")
public record DerivationUpdateResponse(

        @Schema(description = "바뀐 설정")
        DerivationResponse derivation,

        @Schema(description = "바뀐 비율로 곧바로 다시 발행한 결과. 24K 가 아직 없거나, "
                + "직접 입력으로 돌렸거나, 값이 그대로면 null")
        QuotePublishResponse published
) {

    public static DerivationUpdateResponse from(DerivationUpdate update) {
        return new DerivationUpdateResponse(
                DerivationResponse.from(update.policy()),
                update.published() == null ? null : QuotePublishResponse.from(update.published()));
    }
}
