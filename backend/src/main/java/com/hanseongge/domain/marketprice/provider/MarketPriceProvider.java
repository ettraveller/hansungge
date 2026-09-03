package com.hanseongge.domain.marketprice.provider;

import com.hanseongge.domain.marketprice.entity.MetalType;

import java.util.List;
import java.util.Optional;

/**
 * 시세 공급자 (설계서 §6.2).
 *
 * <p>외부 연동을 인터페이스 뒤에 두는 것은 코딩규칙 P5 다. KRX API 의 약관·형식이
 * 바뀌거나(TODO B-1) 유료 벤더로 갈아타도 바뀌는 것은 구현체 하나뿐이고,
 * 시세 파이프라인(L1→L2→L3)과 안전장치는 손대지 않는다.
 *
 * <p>구현체는 <b>절대로 최종 시세를 만들지 않는다.</b> 원본 후보를 돌려줄 뿐이고,
 * 검증·정책 적용·발행은 전부 서비스 계층의 몫이다.
 */
public interface MarketPriceProvider {

    /** {@code market_price_sources.code} 와 일치해야 한다 (MANUAL / KRX). */
    String sourceCode();

    /**
     * 시세 조회.
     *
     * @return 값이 없으면 {@link Optional#empty()} — <b>정상이지만 새 시세가 없는 상태</b>다.
     *         실패는 empty 가 아니라 {@link MarketPriceFetchException} 으로 알린다.
     * @throws MarketPriceFetchException HTTP 오류·타임아웃·파싱 실패 등. 종류가 그대로 수집 로그가 된다
     */
    Optional<RawPrice> fetch(MetalType metal);

    /**
     * 스케줄러가 주기적으로 호출할 대상인가.
     *
     * <p>수동 고시는 관리자 API 로 들어오므로 폴링 대상이 아니다. 이 구분이 없으면
     * 수집기가 30분마다 {@code ManualProvider} 를 호출해 매번 "값 없음" 로그만 쌓는다.
     */
    default boolean pollable() {
        return false;
    }

    /** 이 공급자가 다룰 수 있는 금속. 폴링 대상일 때만 의미가 있다. */
    default List<MetalType> supportedMetals() {
        return List.of();
    }
}
