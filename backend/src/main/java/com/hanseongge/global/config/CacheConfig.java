package com.hanseongge.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 애플리케이션 캐시 (Caffeine, 인메모리).
 *
 * <p>설계서 §7.3 — Redis 는 트래픽이 실제로 발생한 뒤에 도입한다. 단일 인스턴스로
 * 출발하므로 인메모리로 충분하고, 분산 캐시를 처음부터 넣으면 운영 대상만 늘어난다.
 *
 * <p>TTL 을 캐시별로 다르게 잡는 이유:
 * <ul>
 *   <li>{@code systemSettings} — 관리자가 바꿀 때 명시적으로 무효화되므로 길게 잡아도 된다.
 *       TTL 은 DB 직접 수정 같은 예외 상황의 안전망이다.</li>
 *   <li>{@code productPrices} — 시세 변경 이벤트로 무효화되지만(STEP 7), 짧은 TTL 을
 *       함께 둬서 이벤트 유실 시에도 오래된 가격이 남지 않게 한다. 가격은 틀리면 안 된다.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SYSTEM_SETTINGS = "systemSettings";
    public static final String MARKET_PRICES = "marketPrices";
    public static final String PRODUCT_PRICES = "productPrices";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(java.util.List.of(SYSTEM_SETTINGS, MARKET_PRICES, PRODUCT_PRICES));

        manager.registerCustomCache(SYSTEM_SETTINGS, Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build());

        manager.registerCustomCache(MARKET_PRICES, Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats()
                .build());

        manager.registerCustomCache(PRODUCT_PRICES, Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats()
                .build());

        return manager;
    }
}
