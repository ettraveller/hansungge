package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;

/**
 * 환산 설정 변경 결과.
 *
 * @param policy    바뀐 설정 (살 때 행을 대표로 담는다 — 두 행은 늘 함께 바뀐다)
 * @param published 바뀐 비율로 곧바로 다시 발행한 결과. 24K 가 아직 없거나, 직접 입력으로
 *                  돌렸거나, 값이 그대로면 null
 */
public record DerivationUpdate(MarketPricePolicy policy, QuotePublished published) {
}
