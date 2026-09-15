package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 발행 결과.
 *
 * @param issued        새로 발행된 행. {@code plan.lines()} 와 순서가 같다
 * @param effectiveFrom 모든 줄이 함께 가진 발행 시각. 이력 화면이 한 번의 고시를 한 묶음으로 보는 근거다
 */
public record QuotePublished(
        QuotePlan plan,
        List<MarketPriceEffective> issued,
        OffsetDateTime effectiveFrom
) {

    public QuotePublished {
        issued = List.copyOf(issued);
    }
}
