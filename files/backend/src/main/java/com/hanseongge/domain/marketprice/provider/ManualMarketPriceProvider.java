package com.hanseongge.domain.marketprice.provider;

import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.global.util.PricePerGram;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 관리자 수동 고시 공급자 — <b>현재 주력 경로</b>다 (설계서 v0.2 확정).
 *
 * <h2>왜 fetch() 가 항상 empty 인가</h2>
 * 수동 고시는 <b>밖에서 가져오는</b> 것이 아니라 <b>관리자가 밀어 넣는</b> 값이다.
 * 스케줄러가 끌어올 원천이 없으므로 {@link #pollable()} 이 false 이고 폴링 대상에서 빠진다.
 * 실제 입력은 {@code POST /api/admin/prices/apply} → {@link #declare} 로 들어온다.
 *
 * <p>그럼에도 이 구현체를 두는 이유: 수동 고시도 <b>L1(원본) → L2(정책) → L3(최종)</b>
 * 파이프라인을 그대로 통과해야 하기 때문이다 (개발계획 §12.3, 결함 D-5). 관리자가 최종가를
 * effective 에 바로 쓰는 지름길을 만들면 "원본이 얼마였나"가 시스템 대부분의 시세에서
 * 사라진다 — 수동이 주력 경로라서 더욱 그렇다.
 */
@Slf4j
@Component
public class ManualMarketPriceProvider implements MarketPriceProvider {

    @Override
    public String sourceCode() {
        return MarketPriceSource.CODE_MANUAL;
    }

    /**
     * 항상 {@link Optional#empty()}. 끌어올 원천이 없다는 사실 자체가 정답이다.
     * 실패가 아니므로 예외를 던지지 않는다.
     */
    @Override
    public Optional<RawPrice> fetch(MetalType metal) {
        return Optional.empty();
    }

    @Override
    public boolean pollable() {
        return false;
    }

    /**
     * 관리자가 고시한 값을 원본 후보로 만든다.
     *
     * <p>돈 단위 입력은 여기 오기 전에 g 로 환산되어 있어야 한다 — 두 단위가 같은 컬럼에
     * 섞이면 사후에 어느 행이 어느 단위인지 판별할 방법이 없다.
     *
     * @param declaredPricePerGram 관리자 입력 원본가 (KRW/1g)
     */
    public RawPrice declare(MetalType metal, BigDecimal declaredPricePerGram, String adminLoginId) {
        PricePerGram price = PricePerGram.of(declaredPricePerGram);
        String payload = """
                {"source":"MANUAL","declaredBy":"%s","pricePerGram":"%s"}"""
                .formatted(escape(adminLoginId), price.value().toPlainString());
        return new RawPrice(metal, price, OffsetDateTime.now(), payload);
    }

    /** login_id 는 영문·숫자·밑줄만 허용되지만, payload 가 JSON 이므로 방어적으로 이스케이프한다. */
    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
