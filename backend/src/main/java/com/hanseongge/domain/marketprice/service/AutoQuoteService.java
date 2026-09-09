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
 * 대비하는 것이 바로 이 경로다 — 피드가 이상한 값을 주면 자동으로 멈춘다.
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
    private static final String AUTO_REASON = "자동고시 — 외부 피드 수집값에 보정 정책 적용";
    private static final String AUTO_ACTOR = "SYSTEM/AUTO-QUOTE";
}
