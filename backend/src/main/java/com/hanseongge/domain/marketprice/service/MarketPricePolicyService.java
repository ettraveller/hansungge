package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 18K·14K 의 고시 방식 — 직접 입력할지, 24K 에서 환산할지.
 *
 * <h2>비율을 바꾸면 바로 다시 나간다</h2>
 * 설정만 저장하고 다음 24K 고시를 기다리면, 비율을 고쳤는데 파는 값은 그대로인 상태가
 * 며칠 갈 수 있다. "고쳐도 아무 일도 안 일어난다"로 보이는 것이 가장 나쁘다. 그래서 지금
 * 24K 고시가에 새 비율을 곱해 곧바로 발행한다. 크게 바뀌면 고시와 똑같이 확인을 받는다.
 *
 * <h2>살 때·팔 때 비율이 같다</h2>
 * 업계 관행이 그렇다(예: 18K 0.735, 14K 0.57 을 매입·판매에 똑같이 적용). 한 금속에
 * 비율 하나만 받으면 두 값을 따로 고치다 한쪽을 잊는 일이 없다.
 *
 * <h2>직접 입력으로 돌리면</h2>
 * 마지막으로 나간 환산값이 그대로 남는다. 새로 고시할 때까지 그 값으로 판다 — 설정을
 * 바꿨다고 시세문의로 빠지지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPricePolicyService {

    private final MarketPricePolicyRepository policyRepository;
    private final MarketPriceService marketPriceService;
    private final AuditLogService auditLogService;

    /** 18K·14K 의 지금 설정. 살 때 행을 대표로 읽는다 — 두 행은 늘 함께 바뀐다. */
    @Transactional(readOnly = true)
    public List<MarketPricePolicy> findDerivations() {
        return MarketPriceService.DERIVABLE.stream()
                .map(metal -> find(metal, PriceSide.SELL))
                .toList();
    }

    /** 이 비율로 바꾸면 18K·14K 가 얼마가 되는지. 아무것도 쓰지 않는다. */
    @Transactional(readOnly = true)
    public QuotePlan previewDerivation(MetalType metalType, BigDecimal ratio) {
        return marketPriceService.planDerivation(metalType, ratio);
    }

    /**
     * 고시 방식 변경.
     *
     * @param ratio              {@code DERIVED} 일 때만 쓴다
     * @param confirmLargeChange 바뀐 비율로 다시 나갈 값이 확인 기준을 넘는다는 것을 확인했는가
     * @throws BusinessException 확인이 필요한데 받지 않았으면. 설정도 시세도 바뀌지 않는다
     */
    @Transactional
    public DerivationUpdate updateDerivation(MetalType metalType, PolicyMode mode, BigDecimal ratio,
                                             String changeReason, String adminLoginId,
                                             boolean confirmLargeChange) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경 사유는 필수입니다.");
        }
        MarketPriceService.requireDerivable(metalType);
        if (mode != PolicyMode.DIRECT && mode != PolicyMode.DERIVED) {
            String message = "고시 방식은 직접 입력 또는 24K 환산만 고를 수 있습니다.";
            throw new BusinessException(ErrorCode.INVALID_INPUT, message + " 요청=" + mode,
                    Map.of("mode", message));
        }

        // 24K 도 잡는다. 비율을 곱하는 사이 24K 가 새로 고시되면, 옛 24K 로 환산한 값이
        // 새 24K 옆에 나간다.
        marketPriceService.lock(List.of(MarketPriceService.DERIVE_BASE, metalType));

        MarketPricePolicy sell = find(metalType, PriceSide.SELL);
        MarketPricePolicy buy = find(metalType, PriceSide.BUY);
        Map<String, Object> before = snapshot(sell);

        QuotePlan plan = null;
        if (mode == PolicyMode.DERIVED) {
            plan = marketPriceService.planDerivation(metalType, ratio);
            marketPriceService.requireConfirmed(plan, confirmLargeChange);

            BigDecimal normalized = MarketPriceService.normalizeRatio(ratio);
            sell.useDerived(MarketPriceService.DERIVE_BASE, normalized, adminLoginId);
            buy.useDerived(MarketPriceService.DERIVE_BASE, normalized, adminLoginId);
        } else {
            sell.useDirect(adminLoginId);
            buy.useDirect(adminLoginId);
        }

        Map<String, Object> after = snapshot(sell);
        after.put("reason", changeReason);
        auditLogService.record(AuditRecord.builder(AuditActions.PRICE_DERIVATION_UPDATE)
                .actor(ActorType.ADMIN, adminLoginId)
                .target(AuditActions.TARGET_MARKET_PRICE, metalType.name())
                .before(before)
                .after(after)
                .build());

        QuotePublished published = null;
        // 비율이 그대로라 값도 그대로면 다시 발행하지 않는다. 같은 값의 행이 이력에 쌓이면
        // 실제로 값이 바뀐 시점을 찾기 어려워진다.
        if (plan != null && !plan.isEmpty() && !plan.allUnchanged()) {
            String ratioText = MarketPriceService.normalizeRatio(ratio).stripTrailingZeros().toPlainString();
            published = marketPriceService.publish(plan, baseRawIds(),
                    "24K 환산 비율 %s 적용 — %s".formatted(ratioText, changeReason),
                    adminLoginId, OffsetDateTime.now());
        }

        log.info("[MARKET-PRICE] 고시 방식 변경 {} → {}{} 재발행={} by={} 사유={}",
                metalType, mode, mode == PolicyMode.DERIVED ? " ×" + ratio : "",
                published != null, adminLoginId, changeReason);
        return new DerivationUpdate(sell, published);
    }

    private MarketPricePolicy find(MetalType metalType, PriceSide priceSide) {
        return policyRepository.findByMetalTypeAndPriceSide(metalType, priceSide)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_POLICY_NOT_FOUND,
                        "정책 없음: " + metalType + "/" + priceSide));
    }

    /** 환산 줄의 근거 원본 — 지금 24K 행이 가리키는 원본을 그대로 잇는다. */
    private Map<PriceSide, Long> baseRawIds() {
        Map<PriceSide, Long> ids = new EnumMap<>(PriceSide.class);
        for (PriceSide side : PriceSide.values()) {
            marketPriceService.findCurrent(MarketPriceService.DERIVE_BASE, side)
                    .map(MarketPriceEffective::getBaseRawId)
                    .ifPresent(id -> ids.put(side, id));
        }
        return ids;
    }

    private static Map<String, Object> snapshot(MarketPricePolicy policy) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mode", policy.getMode().name());
        values.put("deriveFrom", policy.getDeriveFrom() == null ? null : policy.getDeriveFrom().name());
        values.put("deriveRatio", policy.getDeriveRatio() == null ? null : policy.getDeriveRatio().toPlainString());
        return values;
    }
}
