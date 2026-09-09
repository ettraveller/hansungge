package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.entity.MarketPriceFetchLog;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.provider.FeedMarketPriceProvider;
import com.hanseongge.domain.marketprice.provider.MarketPriceFetchException;
import com.hanseongge.domain.marketprice.provider.MarketPriceProvider;
import com.hanseongge.domain.marketprice.provider.RawPrice;
import com.hanseongge.domain.marketprice.repository.MarketPriceFetchLogRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceSourceRepository;
import com.hanseongge.domain.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 자동 수집기 (설계서 §6.2).
 *
 * <h2>실패가 시세를 건드리지 않는다</h2>
 * 이 클래스는 <b>원본을 만들거나 안 만들거나</b> 둘 중 하나만 한다. 최종 시세를 직접
 * 건드리는 코드가 없다. 그래서 어떤 실패 경로에서도 결과는 같다 — 원본이 안 생기고,
 * 원본이 없으면 발행도 없고, 발행이 없으면 <b>기존 시세가 그대로 유지된다</b> (원칙 P6).
 *
 * <h2>수집과 발행을 분리한 이유</h2>
 * 수집 성공이 곧 발행은 아니다. 수집된 원본은 변동률 검사를 거쳐야 하고, 초과하면 승인
 * 대기로 간다. 수집기가 발행까지 하면 그 판단이 수집 로직에 섞여 둘 다 검증하기 어려워진다.
 *
 * <p>현재 폴링 대상은 없다 — {@code ManualProvider} 는 폴링 대상이 아니고, KRX 는
 * 약관 확인(TODO B-1) 전까지 비활성이다. 그래도 이 골격을 먼저 두는 이유는, KRX 가
 * 켜지는 날 바뀌는 것이 <b>Provider 구현 하나뿐</b>이게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceCollector {

    /** 재시도 횟수 (개발계획 §12.4 "지수 백오프 재시도(3회)"). */
    private static final int MAX_ATTEMPTS = 3;
    /** 재시도 간격의 기준. 시도마다 2배로 늘어난다. */
    private static final long BACKOFF_BASE_MILLIS = 500L;

    /** {@code system_settings.MARKET_FETCH_CRON} 이 없거나 잘못됐을 때의 기본 주기. */
    public static final String DEFAULT_CRON = "0 0/30 10-18 * * MON-FRI";
    public static final String SETTING_CRON = "MARKET_FETCH_CRON";

    private final List<MarketPriceProvider> providers;
    private final MarketPriceSourceRepository sourceRepository;
    private final MarketPriceFetchLogRepository fetchLogRepository;
    private final MarketPriceCollectionWriter writer;
    private final AutoQuoteService autoQuoteService;
    private final SystemSettingService systemSettingService;

    /**
     * 스케줄러 진입점.
     *
     * <p>트랜잭션을 여기서 열지 않는다. 금속 하나의 실패가 다른 금속의 수집까지 롤백시키면,
     * 한 종목의 장애가 전체 수집을 멈추게 된다. 쓰기는 {@link MarketPriceCollectionWriter}
     * 가 건별 독립 트랜잭션으로 처리한다.
     */
    public void collectAll() {
        List<MarketPriceProvider> pollable = providers.stream()
                .filter(MarketPriceProvider::pollable)
                .toList();

        if (pollable.isEmpty()) {
            log.debug("[MARKET-PRICE] 폴링 대상 공급자가 없습니다. 수집을 건너뜁니다.");
            return;
        }

        for (MarketPriceProvider provider : pollable) {
            Optional<MarketPriceSource> source = sourceRepository.findByCode(provider.sourceCode());
            if (source.isEmpty()) {
                log.error("[MARKET-PRICE] 등록되지 않은 소스 코드입니다: {}", provider.sourceCode());
                continue;
            }
            if (!source.get().isActive()) {
                // 소스 비활성은 정상 운영 상태다(KRX 는 약관 확인 전까지 꺼둔다).
                // 실패로 기록하지 않는다 — 대시보드가 붉게 물들면 진짜 장애가 묻힌다.
                log.debug("[MARKET-PRICE] 소스 {} 가 비활성입니다. 건너뜁니다.", provider.sourceCode());
                continue;
            }
            for (MetalType metal : provider.supportedMetals()) {
                collectOne(provider, source.get(), metal);
            }
        }
    }

    /**
     * 한 (공급자 × 금속) 수집. 재시도까지 포함한다.
     *
     * <p>예외를 밖으로 내보내지 않는다. 여기서 터지면 뒤의 금속들이 수집되지 않는다.
     */
    public void collectOne(MarketPriceProvider provider, MarketPriceSource source, MetalType metal) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            OffsetDateTime startedAt = OffsetDateTime.now();
            long startNanos = System.nanoTime();
            try {
                // 살 때·팔 때를 각각 고시하는 피드는 두 값을 함께 받는다. 그런 원천에서
                // 한쪽만 저장하면, 저장되지 않은 쪽 정책이 반대편 값을 기준으로 삼는다.
                if (provider instanceof FeedMarketPriceProvider feed) {
                    List<FeedMarketPriceProvider.SidedPrice> both = feed.fetchBothSides(metal);
                    if (both.isEmpty()) {
                        log.debug("[MARKET-PRICE] {} {} 신규 시세 없음", provider.sourceCode(), metal);
                        return;
                    }
                    // 두 원본을 먼저 모두 적재한 뒤 <b>짝으로</b> 발행한다.
                    //
                    // 한 쪽씩 저장하며 바로 발행하면, 살 때가 임계를 넘어 보류된 뒤에
                    // 팔 때가 정상 발행되어 "한쪽만 움직인 시세표"가 된다. 금값이
                    // 움직이면 살 때와 팔 때는 같이 움직여야 한다.
                    Long sellRawId = null;
                    Long buyRawId = null;
                    for (FeedMarketPriceProvider.SidedPrice sided : both) {
                        Long rawId = writer.store(source, metal, sided.side(), sided.raw(),
                                elapsedMs(startNanos), attempt, startedAt);
                        if (sided.side() == PriceSide.SELL) sellRawId = rawId;
                        else buyRawId = rawId;
                    }

                    // rawId 가 null 이면 검증에 걸렸거나 이미 받아 둔 고시다.
                    // 한쪽이라도 없으면 publishPair 가 아무것도 발행하지 않는다.
                    autoQuoteService.publishPair(metal, sellRawId, buyRawId);
                    return;
                }

                Optional<RawPrice> fetched = provider.fetch(metal);
                if (fetched.isEmpty()) {
                    // 실패가 아니다. 가져올 새 값이 없을 뿐이므로 재시도도 실패기록도 하지 않는다.
                    log.debug("[MARKET-PRICE] {} {} 신규 시세 없음", provider.sourceCode(), metal);
                    return;
                }
                writer.store(source, metal, fetched.get(), elapsedMs(startNanos), attempt, startedAt);
                return;

            } catch (MarketPriceFetchException e) {
                writer.recordFailure(source, metal, e.getResult(), e.getHttpStatus(),
                        elapsedMs(startNanos), e.getMessage(), attempt, startedAt);

                if (attempt == MAX_ATTEMPTS || !retryable(e.getResult())) {
                    log.warn("[MARKET-PRICE] {} {} 수집 실패({}). 기존 시세를 그대로 유지합니다.",
                            provider.sourceCode(), metal, e.getResult());
                    return;
                }
                backoff(attempt);

            } catch (Exception e) {
                writer.recordFailure(source, metal, FetchResult.EXCEPTION, null,
                        elapsedMs(startNanos), e.getMessage(), attempt, startedAt);
                log.error("[MARKET-PRICE] {} {} 수집 중 예상치 못한 오류. 기존 시세를 그대로 유지합니다.",
                        provider.sourceCode(), metal, e);
                return;
            }
        }
    }

    // =================================================================
    // 상태 조회
    // =================================================================

    /** 관리자 대시보드용 수집 상태 (요구사항 4 — 마지막 성공시각 포함). */
    @Transactional(readOnly = true)
    public CollectionStatus status(boolean schedulerEnabled) {
        OffsetDateTime lastSuccess = fetchLogRepository
                .findFirstByResultOrderByStartedAtDescIdDesc(FetchResult.SUCCESS)
                .map(MarketPriceFetchLog::getStartedAt)
                .orElse(null);

        Optional<MarketPriceFetchLog> lastAttempt = fetchLogRepository.findFirstByOrderByStartedAtDescIdDesc();
        long failures = fetchLogRepository.count() - fetchLogRepository.countByResult(FetchResult.SUCCESS);

        return new CollectionStatus(
                schedulerEnabled,
                currentCron(),
                lastAttempt.map(MarketPriceFetchLog::getStartedAt).orElse(null),
                lastAttempt.map(MarketPriceFetchLog::getResult).orElse(null),
                lastSuccess,
                failures);
    }

    /** {@code system_settings.MARKET_FETCH_CRON}. 없으면 기본 주기. */
    public String currentCron() {
        return systemSettingService.get(SETTING_CRON, DEFAULT_CRON);
    }

    // =================================================================
    // 내부
    // =================================================================

    /**
     * 재시도할 가치가 있는 실패인가.
     *
     * <p>값이 이상하거나 파싱이 안 되는 것은 <b>다시 불러도 똑같다.</b> 그런 실패를 3번
     * 반복하면 로그만 3배로 늘고 외부 API 쿼터만 소모한다.
     */
    private boolean retryable(FetchResult result) {
        return result == FetchResult.HTTP_ERROR
                || result == FetchResult.TIMEOUT
                || result == FetchResult.EXCEPTION;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_BASE_MILLIS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }
}
