package com.hanseongge.domain.marketprice.provider;

import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * KRX 자동 수집 공급자 — <b>아직 구현하지 않은 껍데기다.</b>
 *
 * <h2>왜 비어 있는가 (TODO B-1)</h2>
 * 수집 방식이 법적으로 확정되지 않았다:
 * <ul>
 *   <li>공공데이터포털 KRX 금시세 API(일별)의 <b>상업적 이용 조건</b> 확인 필요</li>
 *   <li>장중 30분 간격이 필요하면 KRX 정보데이터시스템 크롤링이 되는데,
 *       그 <b>약관 저촉 여부</b>가 미확인이다</li>
 * </ul>
 * 확인 전에 동작하는 수집기를 넣으면, 켜는 순간 약관 위반이 될 수 있다. 그래서 코드는
 * 두되 <b>기본 비활성</b>({@code @ConditionalOnProperty})으로 두고, 호출되면 명확히 실패한다.
 * 조용히 empty 를 돌려주지 않는 이유는, 그 경우 "수집이 되는데 값이 없는 상태"로 오인되어
 * 아무도 미구현 사실을 눈치채지 못하기 때문이다.
 *
 * <h2>활성화 방법</h2>
 * <pre>
 * app.market-price.krx.enabled=true
 * </pre>
 * 그리고 {@code market_price_sources} 의 {@code KRX} 행을 {@code active=true} 로 바꾼다.
 * 두 곳을 모두 켜야 도는 것은 의도적이다 — 설정 하나를 실수로 건드려 수집이 시작되는 것을 막는다.
 *
 * <h2>구현 시 지켜야 할 것</h2>
 * <ol>
 *   <li>실패는 {@link MarketPriceFetchException} 으로 종류를 구분해 던진다
 *       (HTTP_ERROR / TIMEOUT / PARSE_ERROR). 그 값이 그대로 수집 로그가 된다</li>
 *   <li>KRX 는 <b>1돈(3.75g) 또는 1g</b> 단위가 원천마다 다르다. 반드시 g 로 환산해서 돌려준다</li>
 *   <li>{@code payloadJson} 에 응답 원문을 그대로 담는다 — 분쟁 시 유일한 근거다</li>
 *   <li>정규장은 09:00~15:30 이다. 15:30 이후 값은 종가 고정이므로 {@code quotedAt} 을
 *       수집시각이 아니라 <b>시세 기준시각</b>으로 채운다</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.market-price.krx.enabled", havingValue = "true")
public class KrxMarketPriceProvider implements MarketPriceProvider {

    @PostConstruct
    void warnNotImplemented() {
        log.warn("""
                [MARKET-PRICE] KrxMarketPriceProvider 가 활성화되었으나 아직 구현되지 않았습니다.
                수집 시도는 EXCEPTION 으로 기록되고 최종 시세는 변하지 않습니다.
                수집 방식 확정(TODO B-1) 전까지는 app.market-price.krx.enabled 를 켜지 마세요.""");
    }

    @Override
    public String sourceCode() {
        return MarketPriceSource.CODE_KRX;
    }

    @Override
    public Optional<RawPrice> fetch(MetalType metal) {
        throw new MarketPriceFetchException(FetchResult.EXCEPTION,
                "KRX 수집기 미구현 (TODO B-1: 공공데이터포털 API 상업적 이용 조건 / 크롤링 약관 확인 대기)");
    }

    @Override
    public boolean pollable() {
        return true;
    }

    /**
     * KRX 가 고시하는 것은 <b>24K 시장가 하나</b>다. 18K·14K 는 파생이고, 은·백금은
     * 별도 원천이 필요하다. 구현 시 이 목록을 실제 지원 범위에 맞춰 넓힌다.
     */
    @Override
    public List<MetalType> supportedMetals() {
        return List.of(MetalType.GOLD_24K);
    }
}
