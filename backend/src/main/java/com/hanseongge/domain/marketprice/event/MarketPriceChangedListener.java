package com.hanseongge.domain.marketprice.event;

import com.hanseongge.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 시세 변경 → 가격 캐시 무효화 (개발계획 §12.7).
 *
 * <h2>상품 가격을 일괄 UPDATE 하는 배치가 없는 이유</h2>
 * 상품 가격은 <b>저장된 값이 아니라 계산식</b>이다({@code 시세 × 중량 + 공임}).
 * 그래서 캐시만 비우면 다음 조회부터 새 시세가 자동으로 반영된다. 이것이
 * "관리자가 1g 최종 시세를 변경하면 상품 가격이 자동 변경된다"는 요구사항의 구현이며,
 * 상품 테이블을 건드리는 배치는 존재하지 않는다.
 *
 * <h2>AFTER_COMMIT 인 이유</h2>
 * 커밋 전에 비우면 롤백된 시세로 캐시가 다시 채워져 DB 에 없는 가격이 화면에 뜬다.
 * 캐시는 항상 <b>확정된 사실</b> 뒤에 따라와야 한다.
 *
 * <h2>감사로그를 여기서 쓰지 않는 이유</h2>
 * §12.7 은 이 지점에서 {@code audit_logs} 도 기록하도록 그렸지만, 실제 기록은
 * {@code MarketPriceService} 가 한다. 행위자·사유·환산 비율 같은 맥락이 서비스에 있고,
 * 이벤트에는 없기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceChangedListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceChanged(MarketPriceChangedEvent event) {
        // 금속별로 골라 비우지 않고 통째로 비운다. 어떤 상품이 어떤 금속을 쓰는지는
        // 상품 도메인(STEP 8~9)의 지식이고, 시세 도메인이 알면 의존이 거꾸로 선다.
        // 가격 캐시 TTL 이 60초라 전체 무효화의 비용도 크지 않다.
        evict(CacheConfig.MARKET_PRICES);
        evict(CacheConfig.PRODUCT_PRICES);

        log.info("[MARKET-PRICE] 시세 변경 반영 — {}/{} {} → {} (id={}, by={})",
                event.metalType(), event.priceSide(),
                event.isFirstIssue() ? "(최초)" : event.previousPricePerGram(),
                event.newPricePerGram(), event.effectiveId(), event.changedBy());
    }

    /**
     * 캐시 무효화 실패가 시세 발행을 되돌리지 못한다 — 이미 커밋된 뒤다.
     * 실패하면 최대 TTL(60초) 동안 옛 가격이 보일 뿐이므로 ERROR 만 남기고 넘어간다.
     */
    private void evict(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.error("[MARKET-PRICE] 가격 캐시 무효화 실패 — cache={}. 최대 TTL 동안 옛 가격이 보일 수 있습니다.",
                    cacheName, e);
        }
    }
}
