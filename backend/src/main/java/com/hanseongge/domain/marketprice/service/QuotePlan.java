package com.hanseongge.domain.marketprice.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 번의 고시로 <b>함께</b> 바뀌는 줄 전체.
 *
 * <p>24K 를 고시하면 환산으로 설정된 18K·14K 가 같은 계획에 들어간다. 확인 여부도 계획
 * 단위로 판단한다 — 24K 는 기준 안인데 환산된 18K 만 기준을 넘는 경우도 확인 대상이다.
 *
 * @param limitPercent 이번 계획에 쓴 확인 기준(%)
 */
public record QuotePlan(List<QuoteLine> lines, BigDecimal limitPercent) {

    public QuotePlan {
        lines = List.copyOf(lines);
    }

    /** 확인 기준을 넘는 줄이 하나라도 있는가. */
    public boolean needsConfirm() {
        return lines.stream().anyMatch(QuoteLine::exceedsLimit);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** 모든 줄이 지금 값과 같은가. 비어 있으면 거짓이다. */
    public boolean allUnchanged() {
        return !lines.isEmpty() && lines.stream().allMatch(QuoteLine::unchanged);
    }
}
