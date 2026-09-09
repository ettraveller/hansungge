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
        return store(source, metal, null, fetched, elapsedMs, attempt, startedAt);
    }

    /**
     * 어느 쪽(살 때/팔 때) 값인지 아는 원본을 저장한다.
     *
     * <p>{@code side} 가 null 이면 종전대로 양측 공통 기준가로 쌓인다.
     *
     * @return 저장된 원본 id. 검증에 걸렸거나 이미 있는 고시면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long store(MarketPriceSource source, MetalType metal, PriceSide side, RawPrice fetched,
                      int elapsedMs, int attempt, OffsetDateTime startedAt) {

        // 직전값 비교는 같은 쪽끼리 해야 뜻이 있다. 살 때 값을 팔 때 값과 견주면
        // 정상적인 매매 스프레드가 매번 "급변동"으로 잡힌다.
        PriceSide compareSide = side == null ? PriceSide.SELL : side;
        PricePerGram previous = marketPriceService
                .findLatestPriceForValidation(metal, compareSide)
                .orElse(null);

        PriceValidation validation = validator.validate(metal, fetched.price(), previous);
        if (!validation.valid()) {
            fetchLogRepository.save(MarketPriceFetchLog.failure(
                    source.getId(), metal, validation.result(), null,
                    elapsedMs, validation.reason(), attempt, startedAt));
            log.warn("[MARKET-PRICE] 수집값 거부 — {}", validation.reason());
            return null;
        }

        // 고시시각(quotedAt)을 그대로 넘긴다. 여기서 now() 를 넣으면 ux_mpr_dedup 이
        // 무력해져, 같은 고시가 수집 주기마다 새 행으로 쌓인다 — 10분 주기면
        // 하루 50행이 같은 값으로 들어찬다.
        OffsetDateTime now = OffsetDateTime.now();
        MarketPriceRaw entity = side == null
                ? MarketPriceRaw.collected(source, metal, fetched.price(), now,
                        fetched.quotedAt(), fetched.payloadJson())
                : MarketPriceRaw.collectedForSide(source, metal, side, fetched.price(), now,
                        fetched.quotedAt(), fetched.payloadJson());

        // 넣기 전에 확인한다. 제약 위반을 잡는 방식은 쓰지 않는다 — REQUIRES_NEW
        // 트랜잭션은 위반이 나는 순간 rollback-only 로 표시되고, 예외를 잡아도 그
        // 표시는 지워지지 않아 커밋에서 UnexpectedRollbackException 으로 다시 터진다.
        //
        // 이미 받아 둔 고시는 실패가 아니라 "새 값이 없음"이다. 실패 로그를 남기지
        // 않는 이유는, 남기면 10분마다 도는 정상 운영에서 실패율이 90% 로 보이고
        // 그 안에 묻힌 진짜 장애를 아무도 찾지 못하기 때문이다.
        if (rawRepository.existsBySourceIdAndMetalTypeAndPriceSideAndQuoteAt(
                source.getId(), metal, side, entity.getQuoteAt())) {
            log.debug("[MARKET-PRICE] {} {} {} 이미 적재된 고시({}) — 건너뜁니다",
                    source.getCode(), metal, side, entity.getQuoteAt());
            return null;
        }

        MarketPriceRaw raw = rawRepository.save(entity);

        fetchLogRepository.save(MarketPriceFetchLog.success(
                source.getId(), metal, raw.getId(), elapsedMs, attempt, startedAt));

        log.info("[MARKET-PRICE] 수집 성공 — {} {} {} {}원/g rawId={}",
                source.getCode(), metal, side == null ? "공통" : side,
                fetched.price().value(), raw.getId());
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
