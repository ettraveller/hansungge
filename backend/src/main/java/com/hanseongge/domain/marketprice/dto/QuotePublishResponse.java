package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.service.QuotePublished;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** 고시 결과. 모든 줄이 같은 발행 시각을 갖는다. */
@Schema(name = "QuotePublishResponse", description = "고시 결과")
public record QuotePublishResponse(

        @Schema(description = "발행 시각")
        OffsetDateTime effectiveFrom,

        @Schema(description = "발행된 줄. currentPricePerGram 은 고시 직전 값이다")
        List<QuoteLineResponse> lines
) {

    public static QuotePublishResponse from(QuotePublished published) {
        return new QuotePublishResponse(
                published.effectiveFrom(),
                published.plan().lines().stream().map(QuoteLineResponse::from).toList());
    }
}
