package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;

/**
 * 환산 설정 변경 결과.
 *
 * <p>살 때와 팔 때를 모두 담는다. 비율이 양쪽 다르므로(판매 0.825 / 매입 0.73) 한 행만
 * 들고 다니면 화면이 반쪽만 보여주게 된다.
 *
 * @param sell      바뀐 살 때(판매) 설정
 * @param buy       바뀐 팔 때(매입) 설정
 * @param published 바뀐 비율로 곧바로 다시 발행한 결과. 24K 가 아직 없거나, 직접 입력으로
 *                  돌렸거나, 값이 그대로면 null
 */
public record DerivationUpdate(MarketPricePolicy sell, MarketPricePolicy buy, QuotePublished published) {
}
