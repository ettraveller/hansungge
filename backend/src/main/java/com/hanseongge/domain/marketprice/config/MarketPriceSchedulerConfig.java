package com.hanseongge.domain.marketprice.config;

import com.hanseongge.domain.marketprice.service.MarketPriceCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 시세 자동 수집 스케줄 등록.
 *
 * <h2>왜 {@code @Scheduled(cron = "...")} 이 아닌가</h2>
 * 수집 주기는 {@code system_settings.MARKET_FETCH_CRON} 에 있는 <b>운영 설정값</b>이다.
 * 어노테이션에 상수로 박으면 주기를 바꿀 때마다 재배포해야 한다. 트리거를 직접 등록하고
 * <b>매 실행마다 설정을 다시 읽으면</b>, 관리자가 값을 바꾼 즉시 다음 실행부터 반영된다.
 *
 * <h2>잘못된 cron 이 들어와도 멈추지 않는다</h2>
 * 설정값이 깨져 있으면 기본 주기로 되돌리고 WARN 을 남긴다. cron 오타 하나로 시세 수집이
 * 조용히 영영 멈추는 것이 더 큰 사고다.
 *
 * <h2>기본 활성 / 테스트 비활성</h2>
 * {@code app.market-price.scheduler.enabled=false} 로 끌 수 있다. 테스트 프로파일에서는
 * 꺼둔다 — 백그라운드 스레드가 테스트 도중 DB 를 건드리면 재현되지 않는 실패가 생긴다.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.market-price.scheduler.enabled",
                       havingValue = "true", matchIfMissing = true)
public class MarketPriceSchedulerConfig implements SchedulingConfigurer {

    private final MarketPriceCollector collector;

    /** 마지막으로 쓴 cron. 값이 바뀔 때만 로그를 남기기 위해 들고 있는다. */
    private final AtomicReference<String> appliedCron = new AtomicReference<>();

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::runCollection, this::nextExecution);
        log.info("[MARKET-PRICE] 자동 수집 스케줄러를 등록했습니다. cron={}", collector.currentCron());
    }

    /**
     * 수집 실행. 예외를 밖으로 내보내지 않는다.
     *
     * <p>스케줄러 태스크가 예외를 던지면 <b>다음 실행이 잡히지 않고 그대로 멈춘다.</b>
     * 수집이 멈춘 사실은 시세가 낡아 "시세문의"가 뜰 때까지 아무도 모른다.
     */
    private void runCollection() {
        try {
            collector.collectAll();
        } catch (Exception e) {
            log.error("[MARKET-PRICE] 수집 실행 중 오류. 다음 주기에 다시 시도합니다.", e);
        }
    }

    /** 매 실행마다 설정을 다시 읽어 다음 시각을 계산한다. */
    private Instant nextExecution(org.springframework.scheduling.TriggerContext context) {
        String cron = resolveCron();
        if (!cron.equals(appliedCron.getAndSet(cron))) {
            log.info("[MARKET-PRICE] 수집 주기 적용 — cron={}", cron);
        }
        return new CronTrigger(cron).nextExecution(context);
    }

    private String resolveCron() {
        String configured = collector.currentCron();
        if (CronExpression.isValidExpression(configured)) {
            return configured;
        }
        log.warn("[MARKET-PRICE] MARKET_FETCH_CRON 값이 올바른 cron 이 아닙니다: '{}'. 기본값 '{}' 을 사용합니다.",
                configured, MarketPriceCollector.DEFAULT_CRON);
        return MarketPriceCollector.DEFAULT_CRON;
    }
}
