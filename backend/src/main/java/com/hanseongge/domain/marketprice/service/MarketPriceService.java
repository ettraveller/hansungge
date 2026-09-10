package com.hanseongge.domain.marketprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePending;
import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.event.MarketPriceChangedEvent;
import com.hanseongge.domain.marketprice.repository.MarketPriceEffectiveRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePendingRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PricePerGram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시세 3층 파이프라인의 중심 (설계서 §6, 개발계획 §12).
 *
 * <pre>
 * L1 원본(raw) ──► L2 정책 적용 ──► [변동률 검사] ──► L3 최종(effective)
 *                                        └─ 초과 ──► L4 승인대기(pending), 발행 없음
 * </pre>
 *
 * <h2>이 클래스가 지키는 것</h2>
 * <ol>
 *   <li><b>원본은 절대 덮어쓰지 않는다.</b> 최종 시세는 원본을 고치는 것이 아니라
 *       새 행을 발행하는 것이다</li>
 *   <li><b>실패가 가격을 0으로 만들지 않는다.</b> 어떤 실패 경로에서도 발행을 하지 않을 뿐
 *       기존 현재값은 그대로다 (원칙 P6)</li>
 *   <li><b>급격한 변동은 자동 반영되지 않는다.</b> 임계값 초과는 발행 대신 승인 대기다</li>
 *   <li><b>사유와 작성자가 없는 적용은 없다.</b> DB NOT NULL 이자 감사 요건이다</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    /** 이상 변동 임계값(%) 설정 키. V2 가 5 로 시드한다. */
    public static final String SETTING_MAX_CHANGE_PERCENT = "MAX_PRICE_CHANGE_PERCENT";
    /** 시세 노후 판정(분) 설정 키. V2 가 240 으로 시드한다. */
    public static final String SETTING_STALE_MINUTES = "PRICE_STALE_MINUTES";

    private static final BigDecimal DEFAULT_MAX_CHANGE_PERCENT = new BigDecimal("5");
    /**
     * 설정이 없을 때의 기본값. <b>0 = 노후 판정 안 함.</b>
     *
     * <p>240(4시간)이었다. 시간만 보고 값을 내리면 주말과 영업시간 뒤가 전부
     * 시세문의가 되는데, 그때는 장이 닫혀 값이 바뀔 이유가 없다.
     */
    private static final int DEFAULT_STALE_MINUTES = 0;

    private final MarketPriceRawRepository rawRepository;
    private final MarketPricePolicyRepository policyRepository;
    private final MarketPriceEffectiveRepository effectiveRepository;
    private final MarketPricePendingRepository pendingRepository;
    private final MarketPriceCalculator calculator;
    private final com.hanseongge.domain.setting.service.SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // =================================================================
    // 적용 (L1 → L2 → L3)
    // =================================================================

    /**
     * 원본에 정책을 적용해 최종 시세를 발행한다.
     *
     * <p>변동률이 임계값을 넘으면 <b>발행하지 않고</b> 승인 대기로 돌린다. 그 경우에도
     * 예외를 던지지 않는다 — 보류는 설계된 정상 경로이고, 호출부는
     * {@link PriceApplyResult#applied()} 로 분기한다.
     *
     * @param changeReason 필수. 비어 있으면 거부한다 (개발계획 §6.3)
     */
    @Transactional
    public PriceApplyResult applyPrice(MetalType metalType, PriceSide priceSide, Long rawId,
                                       String changeReason, String adminLoginId) {
        return applyPrice(metalType, priceSide, rawId, changeReason, adminLoginId, null);
    }

    /**
     * 발행 시각을 지정해 적용한다.
     *
     * <p>살 때와 팔 때를 함께 고시할 때 <b>같은 시각</b>을 넘기기 위한 것이다.
     * 각자 {@code now()} 를 찍으면 몇 밀리초가 어긋나고, 그 차이 때문에 이력 화면이
     * 한 번의 고시를 <b>두 줄</b>로 — 그것도 각각 한쪽이 비어 있는 채로 — 보여준다.
     * 실제로 그렇게 나왔다.
     *
     * @param issuedAt null 이면 지금 시각을 쓴다
     */
    @Transactional
    public PriceApplyResult applyPrice(MetalType metalType, PriceSide priceSide, Long rawId,
                                       String changeReason, String adminLoginId,
                                       OffsetDateTime issuedAt) {
        requireReason(changeReason);

        MarketPriceRaw raw = rawRepository.findById(rawId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_RAW_NOT_FOUND, "원본 없음: " + rawId));
        if (raw.getMetalType() != metalType) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "원본 금속 불일치: raw=%s 요청=%s".formatted(raw.getMetalType(), metalType));
        }

        MarketPricePolicy policy = findPolicy(metalType, priceSide);
        PriceCalculation calculation = calculate(raw.price(), policy);

        // 이 지점부터 (metal, side) 를 배타적으로 잡는다. 트랜잭션 종료 시 자동 해제된다.
        effectiveRepository.acquireLock(lockKey(metalType, priceSide));

        Optional<MarketPriceEffective> currentOpt =
                effectiveRepository.findByMetalTypeAndPriceSideAndEffectiveToIsNull(metalType, priceSide);

        BigDecimal changePercent = currentOpt
                .map(current -> calculation.result().changePercentFrom(current.price()))
                .orElse(null);

        if (exceedsLimit(changePercent, metalType, priceSide)) {
            return hold(raw, metalType, priceSide, calculation, currentOpt.orElseThrow(),
                    changePercent, changeReason, adminLoginId);
        }

        return issue(raw.getId(), metalType, priceSide, calculation, policy,
                currentOpt.orElse(null), changePercent, changeReason, adminLoginId, issuedAt);
    }

    /**
     * 살 때·팔 때를 <b>함께</b> 적용한다.
     *
     * <h2>원칙</h2>
     * 금값이 움직이면 살 때와 팔 때가 같이 움직인다. 한쪽만 새 값이 되는 상태는
     * 존재하지 않아야 한다 — 그것은 시세가 아니라 고장이다.
     *
     * <h2>그래서 먼저 재고, 그다음에 쓴다</h2>
     * 한쪽씩 {@code applyPrice} 를 부르면 첫 쪽이 이미 발행된 뒤에야 둘째 쪽이
     * 임계를 넘는다는 사실을 알게 된다. 그 시점에는 되돌릴 수 없다(발행은 append-only 다).
     *
     * <p>그래서 두 쪽의 변동률을 <b>모두 계산한 뒤에</b> 판단한다.
     * 한쪽이라도 임계를 넘으면 <b>둘 다 보류</b>한다. 통과한 쪽만 내보내면 바로 그
     * "한쪽만 움직인 시세표"가 된다.
     *
     * @return 살 때 결과와 팔 때 결과. 둘 다 발행되었거나, 둘 다 보류다
     */
    @Transactional
    public PairedApplyResult applyPricePair(MetalType metalType, Long sellRawId, Long buyRawId,
                                            String changeReason, String adminLoginId) {
        requireReason(changeReason);

        Plan sell = plan(metalType, PriceSide.SELL, sellRawId);
        Plan buy = plan(metalType, PriceSide.BUY, buyRawId);

        // 한쪽이라도 넘으면 둘 다 보류한다.
        boolean hold = sell.exceeds() || buy.exceeds();

        if (hold) {
            log.warn("[MARKET-PRICE] {} 변동률 초과로 양쪽 모두 보류합니다 (살때 {}% / 팔때 {}%).",
                    metalType, sell.changePercent(), buy.changePercent());
            return new PairedApplyResult(holdOf(sell, changeReason, adminLoginId),
                    holdOf(buy, changeReason, adminLoginId));
        }

        // 두 쪽에 같은 발행 시각을 준다. 이력 화면이 한 번의 고시를 한 줄로 묶는 근거다.
        OffsetDateTime issuedAt = OffsetDateTime.now();
        return new PairedApplyResult(
                issueOf(sell, changeReason, adminLoginId, issuedAt),
                issueOf(buy, changeReason, adminLoginId, issuedAt));
    }

    /**
     * 적용하기 전에 계산만 해 둔 것.
     *
     * <p>보류 판단에는 <b>양쪽 결과가 모두</b> 필요하다. 그래서 계산과 쓰기를 나눈다.
     */
    private record Plan(MarketPriceRaw raw, MetalType metalType, PriceSide priceSide,
                        MarketPricePolicy policy, PriceCalculation calculation,
                        MarketPriceEffective current, BigDecimal changePercent, boolean exceeds) {
    }

    private Plan plan(MetalType metalType, PriceSide priceSide, Long rawId) {
        MarketPriceRaw raw = rawRepository.findById(rawId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_RAW_NOT_FOUND, "원본 없음: " + rawId));
        if (raw.getMetalType() != metalType) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "원본 금속 불일치: raw=%s 요청=%s".formatted(raw.getMetalType(), metalType));
        }

        MarketPricePolicy policy = findPolicy(metalType, priceSide);
        PriceCalculation calculation = calculate(raw.price(), policy);

        effectiveRepository.acquireLock(lockKey(metalType, priceSide));

        MarketPriceEffective current = effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(metalType, priceSide)
                .orElse(null);

        BigDecimal changePercent = current == null
                ? null
                : calculation.result().changePercentFrom(current.price());

        return new Plan(raw, metalType, priceSide, policy, calculation, current, changePercent,
                exceedsLimit(changePercent, metalType, priceSide));
    }

    private PriceApplyResult holdOf(Plan p, String changeReason, String adminLoginId) {
        if (p.current() == null) {
            // 비교할 직전 값이 없으면 애초에 임계를 넘을 수 없다. 반대쪽 때문에 여기
            // 온 것이므로, 보류 대신 그대로 발행하면 한쪽만 움직인다 — 그래서 보류로
            // 맞춘다. 직전 값이 없으면 hold() 가 쓸 current 도 없으므로 원본만 남긴다.
            p.raw().markAnomaly();
            log.warn("[MARKET-PRICE] {}/{} 는 직전 값이 없어 보류 기록만 남깁니다.",
                    p.metalType(), p.priceSide());
            return PriceApplyResult.held(null, p.changePercent(), p.calculation());
        }
        return hold(p.raw(), p.metalType(), p.priceSide(), p.calculation(), p.current(),
                p.changePercent(), changeReason, adminLoginId);
    }

    private PriceApplyResult issueOf(Plan p, String changeReason, String adminLoginId,
                                     OffsetDateTime issuedAt) {
        return issue(p.raw().getId(), p.metalType(), p.priceSide(), p.calculation(), p.policy(),
                p.current(), p.changePercent(), changeReason, adminLoginId, issuedAt);
    }

    /**
     * 바뀐 보정값을 <b>지금 시세에 다시 적용</b>한다.
     *
     * <h2>왜 필요한가</h2>
     * 보정값을 바꿔도 이미 발행된 시세는 바뀌지 않는다. append-only 이력이 과거
     * 시점의 판매가를 그대로 증명해야 하기 때문이다. 그래서 새 보정은 <b>다음
     * 고시부터</b> 적용된다.
     *
     * <p>그런데 다음 고시가 언제 올지 모르는 상황(자동 수집이 없을 때)에서는 그
     * 규칙이 "고쳐도 아무 일도 안 일어난다"로 보인다. 마진을 바꿨는데 파는 값은
     * 그대로인 상태가 며칠 갈 수 있다.
     *
     * <p>그래서 <b>가장 최근 원본에 새 보정을 적용해 새로 발행</b>하는 길을 둔다.
     * 과거 행은 그대로 두고 새 행을 쌓으므로 이력은 온전하다.
     *
     * <h2>오래된 원본으로는 하지 않는다</h2>
     * 노후 판정은 발행 시각({@code effective_from}) 기준이다. 그래서 오래된 원본을
     * 다시 발행하면 <b>옛 값이 방금 것처럼 되살아난다</b> — 시세문의로 막혀 있던
     * 상품이 낡은 가격으로 다시 팔리기 시작한다.
     *
     * <p>원본의 기준시각이 이미 노후 한계를 넘었으면 거절한다. 그 경우 할 일은
     * 재적용이 아니라 새로 고시하는 것이다.
     */
    @Transactional
    public PairedApplyResult reapplyCurrentPolicy(MetalType metalType, String changeReason,
                                                  String adminLoginId) {
        requireReason(changeReason);

        MarketPriceRaw sellRaw = latestRawFor(metalType, PriceSide.SELL);
        MarketPriceRaw buyRaw = latestRawFor(metalType, PriceSide.BUY);

        // 노후 판정을 끈 운영에서는 여기서도 막지 않는다. 화면에서는 옛 값으로
        // 계속 파는데 재적용만 거절하면, 같은 값을 두고 두 규칙이 다른 말을 한다.
        int staleMinutes = systemSettingService.getInt(SETTING_STALE_MINUTES, DEFAULT_STALE_MINUTES);
        if (staleMinutes > 0) {
            OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(staleMinutes);
            requireFresh(sellRaw, threshold, staleMinutes, "살 때");
            requireFresh(buyRaw, threshold, staleMinutes, "팔 때");
        }

        log.info("[MARKET-PRICE] 보정값 재적용 — {} 원본 살때={} 팔때={} by={}",
                metalType, sellRaw.getId(), buyRaw.getId(), adminLoginId);

        return applyPricePair(metalType, sellRaw.getId(), buyRaw.getId(), changeReason, adminLoginId);
    }

    private MarketPriceRaw latestRawFor(MetalType metalType, PriceSide priceSide) {
        return rawRepository
                .findLatestForSide(metalType, priceSide, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_RAW_NOT_FOUND,
                        "%s 의 원본 시세가 없습니다. 먼저 고시해 주세요.".formatted(metalType)));
    }

    private void requireFresh(MarketPriceRaw raw, OffsetDateTime threshold, int staleMinutes,
                              String label) {
        if (raw.getQuoteAt().isBefore(threshold)) {
            throw new BusinessException(ErrorCode.PRICE_INVALID_VALUE,
                    ("%s 원본이 %d분(노후 한계)보다 오래되었습니다 (기준시각 %s). "
                            + "오래된 값을 다시 발행하면 방금 고시한 것처럼 보여 낡은 가격으로 "
                            + "팔리게 됩니다. 재적용 대신 새로 고시해 주세요.")
                            .formatted(label, staleMinutes, raw.getQuoteAt()));
        }
    }

    /**
     * 승인된 보류건을 적용한다.
     *
     * <p>변동률 검사를 <b>다시 하지 않는다.</b> 임계값을 넘었다는 사실을 알고도 관리자가
     * 승인한 것이므로, 여기서 또 막으면 승인이라는 행위 자체가 무의미해진다.
     */
    @Transactional
    public PriceApplyResult applyApproved(MarketPricePending pending, String changeReason, String adminLoginId) {
        requireReason(changeReason);

        MarketPriceRaw raw = rawRepository.findById(pending.getRawId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_RAW_NOT_FOUND,
                        "승인건의 원본 없음: " + pending.getRawId()));
        MarketPricePolicy policy = findPolicy(pending.getMetalType(), pending.getPriceSide());

        // 보류 당시 계산 결과를 그대로 쓴다. 승인 시점에 정책이 바뀌어 있어도 관리자가
        // 승인한 것은 "그때 그 값"이다 — 다시 계산하면 화면에서 본 값과 다른 값이 발행된다.
        PriceCalculation calculation = new PriceCalculation(
                raw.price(), pending.calculatedPrice(),
                pending.calculatedPrice().value().subtract(raw.price().value()),
                policy.getMode());

        effectiveRepository.acquireLock(lockKey(pending.getMetalType(), pending.getPriceSide()));

        MarketPriceEffective current = effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(pending.getMetalType(), pending.getPriceSide())
                .orElse(null);

        return issue(raw.getId(), pending.getMetalType(), pending.getPriceSide(), calculation, policy,
                current, pending.getChangePercent(), changeReason, adminLoginId);
    }

    /** 임계값 초과 → 발행하지 않고 승인 대기로 돌린다. 현재 시세는 직전 값 그대로 유지된다. */
    private PriceApplyResult hold(MarketPriceRaw raw, MetalType metalType, PriceSide priceSide,
                                  PriceCalculation calculation, MarketPriceEffective current,
                                  BigDecimal changePercent, String changeReason, String adminLoginId) {

        // 원본은 보존하되 "이상"으로 표시한다. 값 자체는 건드리지 않는다.
        raw.markAnomaly();

        Optional<MarketPricePending> existing = pendingRepository
                .findFirstByMetalTypeAndPriceSideAndStatusOrderByCreatedAtDesc(
                        metalType, priceSide, PendingStatus.PENDING);
        if (existing.isPresent()) {
            // 같은 조합에 이미 대기건이 있으면 새로 만들지 않는다. 수집 주기마다 쌓이면
            // 관리자가 어느 것을 승인해야 할지 알 수 없게 된다.
            log.warn("[MARKET-PRICE] {}/{} 이미 승인 대기 중(id={}). 새 대기건을 만들지 않습니다.",
                    metalType, priceSide, existing.get().getId());
            return PriceApplyResult.held(existing.get(), changePercent, calculation);
        }

        MarketPricePending pending = pendingRepository.save(MarketPricePending.of(
                raw.getId(), metalType, priceSide,
                calculation.result(), current.price(), changePercent));

        log.warn("[MARKET-PRICE] 변동률 초과로 적용을 보류했습니다 — {}/{} {} → {} ({}%). 현재 시세는 유지됩니다.",
                metalType, priceSide, current.getPricePerGram(),
                calculation.result().value(), changePercent);

        auditLogService.record(AuditRecord.builder(AuditActions.PRICE_APPLY_HELD)
                .actor(ActorType.ADMIN, adminLoginId)
                .target(AuditActions.TARGET_MARKET_PRICE, metalType + "/" + priceSide)
                .before(Map.of("currentPricePerGram", current.getPricePerGram().toPlainString()))
                .after(new LinkedHashMap<>(Map.of(
                        "heldPricePerGram", calculation.result().value().toPlainString(),
                        "changePercent", String.valueOf(changePercent),
                        "pendingId", pending.getId(),
                        "reason", changeReason)))
                .build());

        return PriceApplyResult.held(pending, changePercent, calculation);
    }

    /** 기존 현재값을 마감하고 새 행을 발행한다. */
    private PriceApplyResult issue(Long rawId, MetalType metalType, PriceSide priceSide,
                                   PriceCalculation calculation, MarketPricePolicy policy,
                                   MarketPriceEffective current, BigDecimal changePercent,
                                   String changeReason, String adminLoginId) {
        return issue(rawId, metalType, priceSide, calculation, policy, current, changePercent,
                changeReason, adminLoginId, null);
    }

    private PriceApplyResult issue(Long rawId, MetalType metalType, PriceSide priceSide,
                                   PriceCalculation calculation, MarketPricePolicy policy,
                                   MarketPriceEffective current, BigDecimal changePercent,
                                   String changeReason, String adminLoginId,
                                   OffsetDateTime issuedAt) {

        OffsetDateTime now = issuedAt == null ? OffsetDateTime.now() : issuedAt;

        if (current != null) {
            current.close(now);
            // 반드시 먼저 내보낸다. JPA 는 INSERT 를 UPDATE 보다 앞세우므로, 이 flush 가
            // 없으면 신규 행(effective_to IS NULL)이 먼저 들어가 부분 UNIQUE 인덱스
            // ux_mpe_current 를 위반한다.
            effectiveRepository.saveAndFlush(current);
        }

        MarketPriceEffective issued = effectiveRepository.save(MarketPriceEffective.issue(
                metalType, priceSide, calculation.result(), rawId,
                calculation.mode(), policySnapshot(policy, calculation), now,
                changeReason, adminLoginId));

        BigDecimal previousPrice = current == null ? null : current.getPricePerGram();

        auditLogService.record(AuditRecord.builder(AuditActions.PRICE_APPLY)
                .actor(ActorType.ADMIN, adminLoginId)
                .target(AuditActions.TARGET_MARKET_PRICE, metalType + "/" + priceSide)
                .before(current == null ? null
                        : Map.of("pricePerGram", previousPrice.toPlainString()))
                .after(auditAfter(calculation, changePercent, changeReason, issued))
                .build());

        // AFTER_COMMIT 에서만 처리된다. 커밋 전에 캐시를 비우면 롤백된 시세가 캐시에 남는다.
        eventPublisher.publishEvent(new MarketPriceChangedEvent(
                metalType, priceSide, previousPrice, issued.getPricePerGram(),
                issued.getId(), adminLoginId, now));

        log.info("[MARKET-PRICE] 시세 발행 — {}/{} 원본 {} {} 보정 → 최종 {} (mode={}, by={})",
                metalType, priceSide, calculation.raw().value(),
                calculation.adjustment().toPlainString(), calculation.result().value(),
                calculation.mode(), adminLoginId);

        return PriceApplyResult.applied(issued, changePercent, calculation);
    }

    // =================================================================
    // 조회
    // =================================================================

    /**
     * 판매에 사용할 수 있는 현재 시세.
     *
     * <p>없거나 노후면 {@link Optional#empty()} 다. <b>0 이나 옛 가격을 대신 돌려주지 않는다</b> —
     * 호출부는 empty 를 받으면 "시세문의"를 표시하고 주문을 막는다 (개발계획 §12.4).
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.MARKET_PRICES, key = "'cur:' + #metalType + ':' + #priceSide")
    public Optional<PricePerGram> getCurrentPrice(MetalType metalType, PriceSide priceSide) {
        CurrentPriceView view = describeCurrent(metalType, priceSide);
        return view.isSellable() ? Optional.of(view.effective().price()) : Optional.empty();
    }

    /** 현재 상태 + 사유. 관리자 화면은 노후된 값도 봐야 하므로 이 형태를 쓴다. */
    @Transactional(readOnly = true)
    public CurrentPriceView describeCurrent(MetalType metalType, PriceSide priceSide) {
        return effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(metalType, priceSide)
                .map(this::classify)
                .orElseGet(() -> CurrentPriceView.none(metalType, priceSide));
    }

    /** 전 금속 × SELL/BUY 현재 상태. 고시된 적 없는 조합도 NONE 으로 채워 빠짐없이 돌려준다. */
    @Transactional(readOnly = true)
    public List<CurrentPriceView> describeAllCurrent() {
        Map<String, MarketPriceEffective> byKey = new LinkedHashMap<>();
        for (MarketPriceEffective e : effectiveRepository.findByEffectiveToIsNullOrderByMetalTypeAscPriceSideAsc()) {
            byKey.put(e.getMetalType() + "/" + e.getPriceSide(), e);
        }

        List<CurrentPriceView> views = new ArrayList<>();
        for (MetalType metal : MetalType.values()) {
            for (PriceSide side : PriceSide.values()) {
                MarketPriceEffective effective = byKey.get(metal + "/" + side);
                views.add(effective == null ? CurrentPriceView.none(metal, side) : classify(effective));
            }
        }
        return views;
    }

    /**
     * metal×side 별 <b>직전</b> 최종 시세. 공개 시세표의 변동폭 계산에 쓴다.
     *
     * <p>키는 {@code "GOLD_24K/SELL"} 형태다. 최초 고시라 직전 행이 없는 조합은 아예
     * 담기지 않으므로, 호출부는 {@code null} 을 "비교 대상 없음"으로 다뤄야 한다 —
     * 0% 로 채우면 "움직이지 않았다"는 거짓 정보가 된다.
     */
    @Transactional(readOnly = true)
    public Map<String, MarketPriceEffective> findPreviousByKey() {
        Map<String, MarketPriceEffective> byKey = new LinkedHashMap<>();
        for (MarketPriceEffective e : effectiveRepository.findLatestClosedPerMetalSide()) {
            byKey.merge(previousKey(e.getMetalType(), e.getPriceSide()), e,
                    (a, b) -> a.getEffectiveTo().isAfter(b.getEffectiveTo()) ? a : b);
        }
        return byKey;
    }

    /** {@link #findPreviousByKey()} 가 쓰는 키. 호출부도 이 메서드로 만든다. */
    public static String previousKey(MetalType metalType, PriceSide priceSide) {
        return metalType + "/" + priceSide;
    }

    @Transactional(readOnly = true)
    public Page<MarketPriceEffective> history(MetalType metalType, PriceSide priceSide, Pageable pageable) {
        return effectiveRepository
                .findByMetalTypeAndPriceSideOrderByEffectiveFromDescIdDesc(metalType, priceSide, pageable);
    }

    @Transactional(readOnly = true)
    public List<MarketPriceEffective> historySince(MetalType metalType, PriceSide priceSide, int days) {
        OffsetDateTime from = OffsetDateTime.now().minusDays(days);
        return effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveFromGreaterThanEqualOrderByEffectiveFromAsc(
                        metalType, priceSide, from);
    }

    /**
     * 한 최종 시세 행의 원본·보정·최종 3값.
     *
     * <p>V2 에는 원본가·보정액 컬럼이 없어(결함 D-11) {@code base_raw_id} 로 원본을 되짚는다.
     * 원본을 못 찾으면 최종가만 담아 돌려준다 — 여기서 예외를 던지면 화면 하나가 통째로
     * 뜨지 않고, 정작 보여줘야 할 현재 시세까지 사라진다.
     */
    @Transactional(readOnly = true)
    public PriceBreakdown breakdown(MarketPriceEffective effective) {
        BigDecimal rawPrice = effective.getBaseRawId() == null ? null
                : rawRepository.findById(effective.getBaseRawId())
                        .map(MarketPriceRaw::getPricePerGram)
                        .orElse(null);
        return PriceBreakdown.of(rawPrice, effective.getPricePerGram());
    }

    /** 직전 최종가. 단위 오입력 검증의 기준값으로 쓴다. */
    @Transactional(readOnly = true)
    public Optional<PricePerGram> findLatestPriceForValidation(MetalType metalType, PriceSide priceSide) {
        return effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(metalType, priceSide)
                .map(MarketPriceEffective::price);
    }

    // =================================================================
    // 내부
    // =================================================================

    /**
     * 발행된 시세를 지금 팔 수 있는 값으로 볼 것인가.
     *
     * <h2>시간만으로 값을 내리지 않는다</h2>
     * {@code PRICE_STALE_MINUTES} 가 0 이하면 노후 판정을 하지 않는다 —
     * <b>한 번 고시한 값은 새 고시가 나올 때까지 그대로 유효하다.</b>
     *
     * <p>시간만 보고 내리면 금요일 저녁에 고시한 값이 토요일 새벽에 시세문의로
     * 빠진다. 그 사이 장이 닫혀 있어 값이 바뀔 이유가 없는데도 그렇다. 주말과
     * 영업시간 뒤가 전부 그 상태가 되므로, 실제로는 "고시가 끊긴 것"이 아니라
     * "아무 일도 없었던 것"에 시세문의를 붙이게 된다.
     *
     * <p>대신 감수하는 것 — 수집이 며칠 죽어 있어도 옛 값으로 계속 팔린다.
     * 그래서 판정을 지우지 않고 설정으로 남겨 둔다. 값을 되돌리려면
     * {@code PRICE_STALE_MINUTES} 를 분 단위 양수로 바꾸면 그때부터 다시 걸린다.
     */
    private CurrentPriceView classify(MarketPriceEffective effective) {
        int staleMinutes = systemSettingService.getInt(SETTING_STALE_MINUTES, DEFAULT_STALE_MINUTES);
        if (staleMinutes <= 0) {
            return CurrentPriceView.available(effective);
        }
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(staleMinutes);
        return effective.getEffectiveFrom().isBefore(threshold)
                ? CurrentPriceView.stale(effective)
                : CurrentPriceView.available(effective);
    }

    private MarketPricePolicy findPolicy(MetalType metalType, PriceSide priceSide) {
        return policyRepository.findByMetalTypeAndPriceSide(metalType, priceSide)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_POLICY_NOT_FOUND,
                        "정책 없음: " + metalType + "/" + priceSide));
    }

    /**
     * 정책 적용. 보정 결과가 0 이하가 되면 {@link PricePerGram} 이 거부하고, 그것을
     * 도메인 예외로 바꾼다 — <b>발행되지 않으므로 기존 시세가 유지된다.</b>
     */
    private PriceCalculation calculate(PricePerGram raw, MarketPricePolicy policy) {
        try {
            return calculator.apply(raw, policy);
        } catch (IllegalArgumentException e) {
            // "보정 결과가 유효하지 않습니다" 만으로는 무엇을 고쳐야 할지 알 수 없다.
            // 실제로 이 메시지를 받고 원인을 못 찾는 일이 있었다. 어떤 값에 무엇을
            // 적용했더니 얼마가 나왔는지, 그래서 무엇을 고쳐야 하는지까지 적는다.
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    ("%s %s 의 보정 결과가 0원 이하입니다. "
                            + "원본 %s원/g 에 %s 을(를) 적용했습니다. "
                            + "보정값이 원본보다 크게 빼고 있지 않은지 확인해 주세요.")
                            .formatted(policy.getMetalType(),
                                    policy.getPriceSide() == PriceSide.SELL ? "살 때" : "팔 때",
                                    raw.value().toPlainString(),
                                    describeAdjustment(policy)));
        }
    }

    /** 오류 메시지에 넣을 보정 설명. 관리자가 화면에서 본 말과 같아야 한다. */
    private static String describeAdjustment(MarketPricePolicy policy) {
        return switch (policy.getMode()) {
            case AUTO -> "보정 없음";
            case FIXED_ADJ -> "정액 보정 %s원".formatted(policy.getAdjAmount().toPlainString());
            case PERCENT_ADJ -> "정률 보정 %s%%".formatted(policy.getAdjPercent().toPlainString());
            case MANUAL -> "지정가 %s원".formatted(
                    policy.getManualPrice() == null ? "(미설정)" : policy.getManualPrice().toPlainString());
        };
    }

    private boolean exceedsLimit(BigDecimal changePercent, MetalType metalType, PriceSide priceSide) {
        if (changePercent == null) {
            // 최초 발행은 비교 대상이 없다. 여기서 막으면 첫 고시가 영원히 승인 대기가 된다.
            return false;
        }
        BigDecimal limit = systemSettingService.getDecimal(SETTING_MAX_CHANGE_PERCENT, DEFAULT_MAX_CHANGE_PERCENT);
        boolean exceeds = changePercent.abs().compareTo(limit) > 0;
        if (exceeds) {
            log.warn("[MARKET-PRICE] {}/{} 변동률 {}% 가 허용치 {}% 를 초과했습니다.",
                    metalType, priceSide, changePercent, limit);
        }
        return exceeds;
    }

    private void requireReason(String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시세 변경 사유는 필수입니다.");
        }
    }

    /** advisory lock 키. metal×side 조합마다 고유해야 한다. */
    private String lockKey(MetalType metalType, PriceSide priceSide) {
        return "market_price:" + metalType.name() + ":" + priceSide.name();
    }

    /**
     * 적용 당시 정책 + 계산 근거를 통째로 담는다.
     *
     * <p>V2 에는 {@code raw_price_per_gram} / {@code adjustment_amount} 컬럼이 없어(결함 D-11)
     * 원본가와 보정액을 여기에 함께 넣는다. 그래야 정책이 나중에 바뀌어도
     * "이 시세가 원본 얼마에 얼마를 보정한 값인가"를 조인 없이 답할 수 있다.
     */
    private String policySnapshot(MarketPricePolicy policy, PriceCalculation calculation) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", policy.getMode().name());
        snapshot.put("adjAmount", policy.getAdjAmount());
        snapshot.put("adjPercent", policy.getAdjPercent());
        snapshot.put("manualPrice", policy.getManualPrice());
        snapshot.put("rawPricePerGram", calculation.raw().value());
        snapshot.put("adjustmentAmount", calculation.adjustment());
        snapshot.put("resultPricePerGram", calculation.result().value());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            // 스냅샷을 못 만들면 발행하지 않는다. 근거 없는 시세를 남기느니 실패가 낫다.
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "정책 스냅샷 직렬화 실패: " + e.getMessage());
        }
    }

    private Map<String, Object> auditAfter(PriceCalculation calculation, BigDecimal changePercent,
                                           String changeReason, MarketPriceEffective issued) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("effectiveId", issued.getId());
        after.put("rawPricePerGram", calculation.raw().value().toPlainString());
        after.put("adjustmentAmount", calculation.adjustment().toPlainString());
        after.put("pricePerGram", calculation.result().value().toPlainString());
        after.put("policyMode", calculation.mode().name());
        after.put("changePercent", changePercent == null ? null : changePercent.toPlainString());
        after.put("reason", changeReason);
        return after;
    }
}
