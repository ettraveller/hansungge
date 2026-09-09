package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.domain.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 자동고시 — 수집된 원본을 정책 보정을 거쳐 <b>바로 발행</b>한다.
 *
 * <h2>왜 별도 빈인가</h2>
 * {@link MarketPriceCollector} 안에 두면 자기 호출이 되어 {@code REQUIRES_NEW} 가
 * 프록시를 타지 못하고 조용히 무시된다. 그러면 한 금속의 발행 실패가 앞서 저장된
 * 다른 금속의 원본까지 되돌린다 — 이 프로젝트에서 같은 이유로 빈을 나눈 것이
 * 이번이 네 번째다.
 *
 * <h2>안전장치를 끄지 않는다</h2>
 * 자동이라도 {@link MarketPriceService#applyPrice} 를 그대로 통과한다. 즉
 * <b>변동률 임계값을 넘으면 발행되지 않고 승인 대기로 간다.</b> 자동고시는 사람 손을
 * 더는 장치이지, 검증을 건너뛰는 지름길이 아니다. 발주 요구의 "사고가 터질 시"에
 * 대비하는 것이 바로 이 경로다 — 수집값이 이상하면 자동으로 멈춘다.
 *
 * <h2>두 겹의 스위치</h2>
 * <ol>
 *   <li>{@code system_settings.PRICE_LINKED_METALS} — 어떤 금속을 연동할지의 전역 차단기</li>
 *   <li>{@code market_price_policies.auto_quote} — 그 금속×side 를 자동으로 낼지</li>
 * </ol>
 * 둘 다 켜져야 발행된다. 금속 단위로 한 번에 끄는 일(전역)과 매수만 수동으로 돌리는
 * 일(개별)은 서로 다른 조작이라, 한 스위치로 겸하면 급할 때 원하는 범위를 못 끈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoQuoteService {

    /** 시세 연동을 허용할 금속. 비어 있으면 <b>전부 차단</b>이다. */
    public static final String SETTING_LINKED_METALS = "PRICE_LINKED_METALS";

    private final MarketPricePolicyRepository policyRepository;
    private final MarketPriceService marketPriceService;
    private final SystemSettingService systemSettingService;

    /**
     * 살 때·팔 때를 <b>함께</b> 자동고시한다.
     *
     * <h2>원칙</h2>
     * 금값이 움직이면 살 때와 팔 때가 같이 움직인다. 한쪽만 새 값이 되는 상태는
     * 시세가 아니라 고장이다.
     *
     * <p>그래서 한쪽만 발행하지 않는다. 다음 경우에는 <b>아무것도</b> 발행하지 않는다:
     * <ul>
     *   <li>연동 대상 금속이 아니다</li>
     *   <li>두 쪽 중 하나라도 수동고시로 설정돼 있다</li>
     *   <li>두 쪽 중 하나의 원본이 없다 (검증 탈락·중복 등)</li>
     * </ul>
     *
     * <p>둘 다 자동이고 원본도 둘 다 있으면 {@link MarketPriceService#applyPricePair}
     * 가 변동률까지 함께 판단한다 — 한쪽이라도 임계를 넘으면 둘 다 보류다.
     *
     * <p>⚠️ 지금은 이 경로를 태울 수집 공급자가 없다. 자동 수집 원천이 정해지면
     * {@code SidedMarketPriceProvider} 를 구현하는 것만으로 여기까지 연결된다
     * (TODO B-1 / B-4).
     *
     * @return 둘 다 발행되었으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishPair(MetalType metal, Long sellRawId, Long buyRawId) {
        if (sellRawId == null || buyRawId == null) {
            // 한쪽 원본이 없으면 반대쪽만 낼 수 없다. 그것이 바로 막으려는 상태다.
            log.debug("[AUTO-QUOTE] {} 한쪽 원본이 없어 건너뜁니다 (살때={} 팔때={})",
                    metal, sellRawId, buyRawId);
            return false;
        }
        if (!linkedMetals().contains(metal)) {
            log.debug("[AUTO-QUOTE] {} 은(는) 연동 대상이 아닙니다 ({})", metal, SETTING_LINKED_METALS);
            return false;
        }

        Optional<MarketPricePolicy> sell =
                policyRepository.findByMetalTypeAndPriceSide(metal, PriceSide.SELL);
        Optional<MarketPricePolicy> buy =
                policyRepository.findByMetalTypeAndPriceSide(metal, PriceSide.BUY);

        if (sell.isEmpty() || buy.isEmpty()) {
            log.warn("[AUTO-QUOTE] {} 정책 행이 없습니다. 자동고시를 건너뜁니다.", metal);
            return false;
        }
        if (!sell.get().isAutoQuote() || !buy.get().isAutoQuote()) {
            // 한쪽만 자동이면 그쪽만 움직인다. 원칙에 어긋나므로 둘 다 내지 않는다.
            log.debug("[AUTO-QUOTE] {} 은(는) 한쪽이 수동고시입니다 (살때={} 팔때={}). 원본만 적재합니다.",
                    metal, sell.get().isAutoQuote(), buy.get().isAutoQuote());
            return false;
        }

        try {
            PairedApplyResult result = marketPriceService.applyPricePair(
                    metal, sellRawId, buyRawId, AUTO_REASON, AUTO_ACTOR);

            if (result.applied()) {
                log.info("[AUTO-QUOTE] 자동고시 완료 — {} 살때·팔때 함께 발행", metal);
            } else {
                log.warn("[AUTO-QUOTE] 자동고시 보류 — {} 변동률 임계 초과. 양쪽 모두 승인 대기로 넘겼습니다.",
                        metal);
            }
            return result.applied();

        } catch (Exception e) {
            log.error("[AUTO-QUOTE] 자동고시 실패 — {}. 기존 시세를 유지합니다.", metal, e);
            return false;
        }
    }

    /**
     * 방금 저장된 원본으로 자동고시를 시도한다.
     *
     * <p>예외를 밖으로 내보내지 않는다. 여기서 터지면 뒤따르는 금속의 수집까지 멈춘다.
     * 발행이 실패해도 원본은 이미 저장되어 있으므로, 관리자가 나중에 그 원본으로
     * 수동 고시할 수 있다 — 데이터가 사라지지는 않는다.
     *
     * @return 발행되었으면 true. 자동고시가 아니거나 보류·실패면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(MetalType metal, PriceSide side, Long rawId) {
        if (rawId == null) {
            return false;
        }
        if (!linkedMetals().contains(metal)) {
            log.debug("[AUTO-QUOTE] {} 은(는) 연동 대상이 아닙니다 ({})", metal, SETTING_LINKED_METALS);
            return false;
        }

        Optional<MarketPricePolicy> policy =
                policyRepository.findByMetalTypeAndPriceSide(metal, side);
        if (policy.isEmpty()) {
            log.warn("[AUTO-QUOTE] {} {} 정책 행이 없습니다. 자동고시를 건너뜁니다.", metal, side);
            return false;
        }
        if (!policy.get().isAutoQuote()) {
            log.debug("[AUTO-QUOTE] {} {} 은(는) 수동고시입니다. 원본만 적재합니다.", metal, side);
            return false;
        }

        try {
            PriceApplyResult result = marketPriceService.applyPrice(
                    metal, side, rawId, AUTO_REASON, AUTO_ACTOR);

            if (result.applied()) {
                log.info("[AUTO-QUOTE] 자동고시 완료 — {} {} rawId={}", metal, side, rawId);
            } else {
                // 보류는 정상 경로다. 변동률이 임계를 넘었다는 뜻이고, 관리자 승인을 기다린다.
                log.warn("[AUTO-QUOTE] 자동고시 보류 — {} {} 변동률 임계 초과. 승인 대기로 넘겼습니다.",
                        metal, side);
            }
            return result.applied();

        } catch (Exception e) {
            log.error("[AUTO-QUOTE] 자동고시 실패 — {} {} rawId={}. 기존 시세를 유지합니다.",
                    metal, side, rawId, e);
            return false;
        }
    }

    /**
     * 연동 허용 금속 목록.
     *
     * <p>설정값이 비었거나 전부 오타면 <b>빈 집합</b>이 되어 아무것도 자동고시되지 않는다.
     * 모르는 이름을 만났을 때 전체 허용으로 넘어가지 않는 것이 중요하다 — 오타 하나가
     * 은·백금까지 자동 판매가로 밀어내는 일은 없어야 한다.
     */
    public Set<MetalType> linkedMetals() {
        String raw = systemSettingService.get(SETTING_LINKED_METALS, "");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<MetalType> out = new LinkedHashSet<>();
        for (String token : Arrays.stream(raw.split(",")).map(String::trim).toList()) {
            if (token.isEmpty()) continue;
            try {
                out.add(MetalType.valueOf(token));
            } catch (IllegalArgumentException e) {
                log.warn("[AUTO-QUOTE] {} 에 알 수 없는 금속 '{}' 이 있습니다 — 무시합니다",
                        SETTING_LINKED_METALS, token);
            }
        }
        return out;
    }

    /** 고시 사유는 필수값이다. 자동 발행분을 이력에서 한눈에 가려낼 수 있게 고정 문구를 쓴다. */
    private static final String AUTO_REASON = "자동고시 — 수집값에 보정 정책 적용";
    private static final String AUTO_ACTOR = "SYSTEM/AUTO-QUOTE";
}
