package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L2 — 보정 정책 조회·변경.
 *
 * <p>정책 변경은 <b>이미 발행된 시세를 바꾸지 않는다.</b> 다음 적용부터 반영된다.
 * 과거 시세는 각자의 {@code policy_snapshot} 을 들고 있으므로, 정책을 바꿔도
 * "그때 그 시세가 어떤 정책으로 나왔는가"는 변하지 않는다.
 *
 * <p>V2 에는 {@code market_price_policy_history} 가 없다(결함 D-4). 그 전까지 변경 이력은
 * {@code audit_logs} 의 before/after 로 남는다 — {@link AuditLog} 어드바이스가
 * {@link AuditContext#before} 로 넘긴 스냅샷을 집어간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPricePolicyService {

    private final MarketPricePolicyRepository policyRepository;
    private final MarketPriceCalculator calculator;

    @Transactional(readOnly = true)
    public List<MarketPricePolicy> findAll() {
        return policyRepository.findAllByOrderByMetalTypeAscPriceSideAsc();
    }

    @Transactional(readOnly = true)
    public MarketPricePolicy find(MetalType metalType, PriceSide priceSide) {
        return policyRepository.findByMetalTypeAndPriceSide(metalType, priceSide)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_POLICY_NOT_FOUND,
                        "정책 없음: " + metalType + "/" + priceSide));
    }

    /**
     * 정책 변경.
     *
     * @param changeReason 필수. 보정값을 왜 바꿨는지가 없으면 나중에 되짚을 수 없다
     */
    @Transactional
    @AuditLog(action = AuditActions.PRICE_POLICY_UPDATE,
              targetType = AuditActions.TARGET_MARKET_PRICE,
              targetId = "#metalType.name() + '/' + #priceSide.name()")
    public MarketPricePolicy update(MetalType metalType, PriceSide priceSide, PolicyMode mode,
                                    BigDecimal adjAmount, BigDecimal adjPercent, BigDecimal manualPrice,
                                    String changeReason, String adminLoginId) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 변경 사유는 필수입니다.");
        }
        validate(mode, adjAmount, adjPercent, manualPrice);

        MarketPricePolicy policy = find(metalType, priceSide);
        AuditContext.before(snapshot(policy));

        policy.change(mode, adjAmount, adjPercent, manualPrice, adminLoginId);
        // 미설정 경고를 냈던 조합이 정상 설정으로 바뀌면, 다음 미설정 상황에서 다시 경고하게 한다.
        calculator.resetWarning(policy);

        log.info("[MARKET-PRICE] 정책 변경 {}/{} mode={} adjAmount={} adjPercent={} manualPrice={} by={} 사유={}",
                metalType, priceSide, mode, adjAmount, adjPercent, manualPrice, adminLoginId, changeReason);
        return policy;
    }

    /**
     * 자동고시를 <b>값과 함께</b> 켠다.
     *
     * <h2>왜 한 번에 받는가</h2>
     * 전에는 "자동으로 전환"과 "보정값 변경"이 따로였다. 그러면 값을 정하지 않은 채
     * 자동이 켜지고, 보정 0 인 상태로 수집값이 그대로 판매가가 된다. 마진 없이 파는
     * 상태이고, 그 사실은 판 뒤에야 드러난다.
     *
     * <p>자동으로 켜는 순간 반드시 답해야 하는 질문이 "얼마를 더할 것인가"다.
     * 그 질문을 화면에서 건너뛸 수 없게 한 흐름으로 묶는다.
     *
     * <h2>한 트랜잭션</h2>
     * 살 때·팔 때의 보정값과 두 쪽의 자동 전환이 함께 저장된다. 하나라도 실패하면
     * 전부 되돌아간다 — 값만 바뀌고 자동은 안 켜지거나, 그 반대인 상태를 만들지 않는다.
     *
     * @param sellAdj 살 때 보정값. mode 에 따라 금액(원/1g) 또는 비율(%)이다
     * @param buyAdj  팔 때 보정값
     */
    @Transactional
    @AuditLog(action = AuditActions.PRICE_POLICY_UPDATE,
              targetType = AuditActions.TARGET_MARKET_PRICE,
              targetId = "#metalType.name()")
    public java.util.List<MarketPricePolicy> startAutoQuote(MetalType metalType, PolicyMode mode,
                                                            BigDecimal sellAdj, BigDecimal buyAdj,
                                                            String changeReason, String adminLoginId) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자동고시 시작 사유는 필수입니다.");
        }
        if (mode != PolicyMode.AUTO && mode != PolicyMode.FIXED_ADJ && mode != PolicyMode.PERCENT_ADJ) {
            // MANUAL(지정가)은 자동고시와 뜻이 부딪힌다 — 수집값을 받아 놓고 무시하는 셈이다.
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "자동고시에는 '받아온 값 그대로' 또는 '금액/비율 더하기·빼기'만 쓸 수 있습니다.");
        }

        MarketPricePolicy sell = find(metalType, PriceSide.SELL);
        MarketPricePolicy buy = find(metalType, PriceSide.BUY);

        AuditContext.before(java.util.Map.of("sell", snapshot(sell), "buy", snapshot(buy)));

        applyAdjustment(sell, mode, sellAdj, adminLoginId);
        applyAdjustment(buy, mode, buyAdj, adminLoginId);

        sell.switchQuoteMode(true, adminLoginId);
        buy.switchQuoteMode(true, adminLoginId);

        calculator.resetWarning(sell);
        calculator.resetWarning(buy);

        log.info("[MARKET-PRICE] 자동고시 시작 {} mode={} 살때={} 팔때={} by={} 사유={}",
                metalType, mode, sellAdj, buyAdj, adminLoginId, changeReason);
        return java.util.List.of(sell, buy);
    }

    /** 모드에 맞는 칸에만 값을 넣는다. 나머지는 0 으로 지운다 — 옛 값이 되살아나지 않게. */
    private void applyAdjustment(MarketPricePolicy policy, PolicyMode mode,
                                 BigDecimal adj, String adminLoginId) {
        BigDecimal value = adj == null ? BigDecimal.ZERO : adj;
        switch (mode) {
            case FIXED_ADJ -> policy.change(mode, value, BigDecimal.ZERO, null, adminLoginId);
            case PERCENT_ADJ -> policy.change(mode, BigDecimal.ZERO, value, null, adminLoginId);
            default -> policy.change(PolicyMode.AUTO, BigDecimal.ZERO, BigDecimal.ZERO, null, adminLoginId);
        }
    }

    /**
     * 자동고시 / 수동고시 전환.
     *
     * <p>{@link #update} 와 분리한 이유 — 사고가 났을 때 관리자가 가장 먼저 하는 조작이
     * "자동을 끈다"이고, 그 순간에 보정값·모드까지 함께 보내라고 요구하면 급한 손이
     * 엉뚱한 값을 밀어 넣는다. 스위치만 내리는 길을 따로 둔다.
     *
     * <p>사유는 여기서도 필수다. 자동에서 수동으로 내린 기록이 남지 않으면, 나중에
     * "왜 그날 시세가 멈춰 있었는가"를 아무도 설명하지 못한다.
     */
    @Transactional
    @AuditLog(action = AuditActions.PRICE_POLICY_UPDATE,
              targetType = AuditActions.TARGET_MARKET_PRICE,
              targetId = "#metalType.name()")
    public java.util.List<MarketPricePolicy> switchQuoteMode(MetalType metalType,
                                                             boolean autoQuote,
                                                             String changeReason,
                                                             String adminLoginId) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "고시 방식 변경 사유는 필수입니다.");
        }

        // 금속 단위로 바꾼다. 살 때만 자동이고 팔 때는 수동인 상태를 만들 수 없다 —
        // 금값이 움직이면 두 값이 같이 움직여야 하고, 한쪽만 자동이면 그쪽만 움직인다.
        MarketPricePolicy sell = find(metalType, PriceSide.SELL);
        MarketPricePolicy buy = find(metalType, PriceSide.BUY);

        AuditContext.before(java.util.Map.of(
                "sell", snapshot(sell),
                "buy", snapshot(buy)));

        sell.switchQuoteMode(autoQuote, adminLoginId);
        buy.switchQuoteMode(autoQuote, adminLoginId);

        log.info("[MARKET-PRICE] 고시 방식 변경 {} (살때·팔때) → {} by={} 사유={}",
                metalType, autoQuote ? "자동고시" : "수동고시", adminLoginId, changeReason);
        return java.util.List.of(sell, buy);
    }

    /**
     * 모드와 값의 정합성 검사.
     *
     * <p>모드에 맞지 않는 값이 들어오는 것을 막는다. 예컨대 {@code MANUAL} 인데 지정가가
     * 없으면 "보정이 걸린 줄 알았는데 원본이 그대로 나가는" 상태가 되고, 그 사실은
     * 시세가 이상하다는 신고가 들어와야 드러난다.
     */
    private void validate(PolicyMode mode, BigDecimal adjAmount, BigDecimal adjPercent, BigDecimal manualPrice) {
        switch (mode) {
            case MANUAL -> {
                if (manualPrice == null || manualPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "MANUAL 정책에는 0보다 큰 manualPrice 가 필요합니다.");
                }
            }
            case FIXED_ADJ -> {
                if (adjAmount == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "FIXED_ADJ 정책에는 adjAmount 가 필요합니다.");
                }
            }
            case PERCENT_ADJ -> {
                if (adjPercent == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "PERCENT_ADJ 정책에는 adjPercent 가 필요합니다.");
                }
                // -100% 이하면 최종가가 0 이하가 된다. 발행 시점이 아니라 설정 시점에 막는다.
                if (adjPercent.compareTo(BigDecimal.valueOf(-100)) <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "adjPercent 는 -100 보다 커야 합니다. 최종가가 0 이하가 됩니다.");
                }
            }
            case AUTO -> { /* 추가 값이 필요 없다 */ }
        }
    }

    /** 감사로그용 변경 전 스냅샷. */
    private Map<String, Object> snapshot(MarketPricePolicy policy) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mode", policy.getMode().name());
        values.put("adjAmount", policy.getAdjAmount());
        values.put("adjPercent", policy.getAdjPercent());
        values.put("manualPrice", policy.getManualPrice());
        return values;
    }
}
