package com.hanseongge.domain.marketprice.event;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 최종 시세가 새로 발행됐다 (설계서 §6.1, 개발계획 §12.7).
 *
 * <p>Spring 인프로세스 이벤트다. 메시지 브로커를 두지 않은 이유는 구독자가 같은 JVM 안에
 * 있고(캐시 무효화), 단일 인스턴스 운영이기 때문이다 — 브로커는 운영 대상만 늘린다.
 *
 * <p><b>AFTER_COMMIT 에서만 처리된다.</b> 커밋 전에 캐시를 비우면, 롤백된 시세로 캐시가
 * 다시 채워져 <b>DB 에 없는 가격이 화면에 뜬다.</b>
 *
 * @param previousPricePerGram 직전 최종가. 최초 발행이면 null
 */
public record MarketPriceChangedEvent(
        MetalType metalType,
        PriceSide priceSide,
        BigDecimal previousPricePerGram,
        BigDecimal newPricePerGram,
        Long effectiveId,
        String changedBy,
        OffsetDateTime changedAt
) {

    public boolean isFirstIssue() {
        return previousPricePerGram == null;
    }
}
