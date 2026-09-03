package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.global.util.PricePerGram;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L2 — 보정 정책 적용 (개발계획 §12.2).
 *
 * <pre>
 * AUTO         최종 = 원본
 * FIXED_ADJ    최종 = 원본 + adj_amount
 * PERCENT_ADJ  최종 = 원본 × (1 + adj_percent/100)
 * MANUAL       최종 = manual_price
 * </pre>
 *
 * <h2>이 클래스에 DB 접근이 없는 이유</h2>
 * 시세 계산은 이 시스템에서 <b>틀리면 가장 비싼 코드</b>다. 순수 함수로 두면 DB·시각·
 * 트랜잭션 없이 값만으로 전수 검증할 수 있다. 실제로 4가지 모드의 정확성은
 * {@code MarketPriceCalculatorTest} 가 BigDecimal 수준에서 고정한다.
 *
 * <p>계산은 전부 {@link PricePerGram} 위에서 이뤄진다. 그 타입이 0 이하를 거부하므로
 * <b>어떤 보정을 넣어도 0원 이하의 최종가는 만들어질 수 없다</b> — 예외로 실패할 뿐이다
 * (원칙 P6).
 */
@Slf4j
@Component
public class MarketPriceCalculator {

    /** MANUAL 인데 지정가가 없는 조합. 같은 경고를 매 계산마다 찍지 않기 위한 표시. */
    private final Set<String> warnedManualWithoutPrice = ConcurrentHashMap.newKeySet();

    /**
     * 원본 + 정책 → 최종가.
     *
     * @throws IllegalArgumentException 보정 결과가 0 이하가 되는 경우
     *         ({@link PricePerGram} 이 거부한다). 호출자는 이를 발행 실패로 처리하고
     *         <b>기존 시세를 그대로 둔다</b>
     */
    public PriceCalculation apply(PricePerGram raw, MarketPricePolicy policy) {
        return switch (policy.getMode()) {
            case AUTO -> PriceCalculation.of(raw, raw, policy.getMode());

            case FIXED_ADJ -> PriceCalculation.of(
                    raw, raw.plusAmount(policy.getAdjAmount()), policy.getMode());

            case PERCENT_ADJ -> PriceCalculation.of(
                    raw, raw.plusPercent(policy.getAdjPercent()), policy.getMode());

            case MANUAL -> PriceCalculation.of(raw, manualPrice(raw, policy), policy.getMode());
        };
    }

    /**
     * MANUAL 모드의 최종가.
     *
     * <p>{@code manual_price} 가 비어 있으면 <b>원본을 그대로 최종가로 쓴다.</b>
     * MANUAL 은 "관리자 지정가 오버라이드"인데 지정가가 설정되지 않았다면 오버라이드할
     * 것이 없다는 뜻이기 때문이다. V2 가 전 금속×side 를 {@code MANUAL} + 지정가 NULL 로
     * 시드하므로, 이 처리가 없으면 <b>수동 고시(주력 경로)가 첫 시도부터 실패</b>한다.
     *
     * <p>대신 조합당 한 번 WARN 을 남긴다. 조용히 넘어가면 "오버라이드가 걸려 있는 줄
     * 알았는데 원본이 그대로 나가고 있었다"를 아무도 모른다.
     */
    private PricePerGram manualPrice(PricePerGram raw, MarketPricePolicy policy) {
        if (policy.hasManualPrice()) {
            return PricePerGram.of(policy.getManualPrice());
        }
        String key = policy.getMetalType() + "/" + policy.getPriceSide();
        if (warnedManualWithoutPrice.add(key)) {
            log.warn("""
                    [MARKET-PRICE] {} 정책이 MANUAL 이지만 manual_price 가 비어 있습니다.
                    지정가가 없으므로 원본을 그대로 최종가로 적용합니다.
                    보정이 필요하면 PUT /api/admin/prices/policies 로 모드를 바꾸세요.""", key);
        }
        return raw;
    }

    /** 정책이 정상 설정으로 바뀌면 다음 미설정 상황에서 다시 경고할 수 있게 되돌린다. */
    public void resetWarning(MarketPricePolicy policy) {
        warnedManualWithoutPrice.remove(policy.getMetalType() + "/" + policy.getPriceSide());
    }
}
