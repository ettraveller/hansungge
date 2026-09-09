package com.hanseongge.domain.marketprice.service;

/**
 * 살 때·팔 때를 한 번에 고시한 결과.
 *
 * <p>{@link #applied()} 는 <b>둘 다</b> 발행됐을 때만 참이다. 한쪽만 나가는 상태는
 * 이 경로에서 만들지 않는다 — 그것이 이 클래스가 존재하는 이유다.
 */
public record PairedApplyResult(
        PriceApplyResult sell,
        PriceApplyResult buy
) {

    /** 둘 다 발행됐는가. 하나라도 보류면 거짓이다. */
    public boolean applied() {
        return sell.applied() && buy.applied();
    }
}
