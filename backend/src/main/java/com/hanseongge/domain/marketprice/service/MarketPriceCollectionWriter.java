package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.entity.MarketPriceFetchLog;
import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.provider.RawPrice;
import com.hanseongge.domain.marketprice.repository.MarketPriceFetchLogRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.global.util.PricePerGram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 수집 결과를 DB 에 쓰는 부분만 떼어낸 빈.
 *
 * <h2>왜 {@link MarketPriceCollector} 안에 두지 않는가</h2>
 * 이 메서드들은 {@code REQUIRES_NEW} 여야 한다 — 한 금속의 저장 실패가 다른 금속의 결과를
 * 되돌리면 안 되고, 실패 로그는 어떤 경우에도 남아야 하기 때문이다. 그런데 같은 클래스
 * 안에 두면 <b>자기 호출이라 프록시를 타지 않아 어노테이션이 조용히 무시된다.</b>
 * 트랜잭션 경계가 "붙어 있는 것처럼 보이지만 실제로는 없는" 상태는 장애가 나기 전까지
 * 드러나지 않으므로, 빈을 나눠 구조적으로 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceCollectionWriter {

    private final MarketPriceRawRepository rawRepository;
    private final MarketPriceFetchLogRepository fetchLogRepository;
    private final MarketPriceValidator validator;
    private final MarketPriceService marketPriceService;

    /**
     * 검증 통과 시에만 원본을 저장한다.
     *
     * <p>검증에 걸리면 원본을 만들지 않는다 → 발행도 없다 → <b>기존 시세가 그대로 유지된다.</b>
     *
     * @return 저장된 원본 id. 검증에 걸렸으면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long store(MarketPriceSource source, MetalType metal, RawPrice fetched,
                      int elapsedMs, int attempt, OffsetDateTime startedAt) {

        // 검증 기준은 판매가 쪽 현재 시세다. 단위 오입력 판정에 직전값이 필요한데,
        // 원본에는 매입/판매 구분이 없으므로 대표로 SELL 을 쓴다.
        PricePerGram previous = marketPriceService
                .findLatestPriceForValidation(metal, PriceSide.SELL)
                .orElse(null);

        PriceValidation validation = validator.validate(metal, fetched.price(), previous);
        if (!validation.valid()) {
            fetchLogRepository.save(MarketPriceFetchLog.failure(
                    source.getId(), metal, validation.result(), null,
                    elapsedMs, validation.reason(), attempt, startedAt));
            log.warn("[MARKET-PRICE] 수집값 거부 — {}", validation.reason());
            return null;
        }

        MarketPriceRaw raw = rawRepository.save(MarketPriceRaw.collected(
                source, metal, fetched.price(), OffsetDateTime.now(), fetched.payloadJson()));

        fetchLogRepository.save(MarketPriceFetchLog.success(
                source.getId(), metal, raw.getId(), elapsedMs, attempt, startedAt));

        log.info("[MARKET-PRICE] 수집 성공 — {} {} {}원/g rawId={}",
                source.getCode(), metal, fetched.price().value(), raw.getId());
        return raw.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(MarketPriceSource source, MetalType metal, FetchResult result,
                              Integer httpStatus, int elapsedMs, String message,
                              int attempt, OffsetDateTime startedAt) {
        fetchLogRepository.save(MarketPriceFetchLog.failure(
                source.getId(), metal, result, httpStatus, elapsedMs, message, attempt, startedAt));
    }
}
