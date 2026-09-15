package com.hanseongge.domain.marketprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceSourceRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 시세 고시 — <b>이 시스템에서 시세가 만들어지는 입구</b>다.
 *
 * <h2>살 때·팔 때를 한 번에 받는다</h2>
 * 한쪽씩 고시할 수 있으면, 살 때를 고시한 순간부터 팔 때를 고시하기까지 <b>살 때만 값이
 * 있는 시세표</b>가 화면에 걸린다. 두 번째 입력을 잊거나 거기서 오류가 나면 그 상태가
 * 그대로 남는다. 그래서 두 값을 함께 받고 한 트랜잭션에서 처리한다.
 *
 * <h2>24K 를 고시하면 18K·14K 가 따라 나간다</h2>
 * 환산으로 설정된 금속은 같은 트랜잭션, 같은 발행 시각으로 함께 발행된다. 24K 만 바뀌고
 * 18K 가 옛 24K 기준으로 남아 있는 순간을 만들지 않는다.
 *
 * <h2>크게 바뀌면 먼저 확인</h2>
 * {@link #preview} 로 무엇이 얼마로 바뀌는지 먼저 보여주고, 확인 기준을 넘는 값이 있으면
 * 확인을 받은 뒤에만 발행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceDeclarationService {

    private final MarketPriceService marketPriceService;
    private final MarketPriceSourceRepository sourceRepository;
    private final MarketPriceRawRepository rawRepository;
    private final ObjectMapper objectMapper;

    /** 고시하면 무엇이 얼마로 바뀌는지. 아무것도 쓰지 않는다. */
    @Transactional(readOnly = true)
    public QuotePlan preview(MetalType metalType, BigDecimal sellPricePerGram, BigDecimal buyPricePerGram) {
        return marketPriceService.planDeclaration(metalType, sellPricePerGram, buyPricePerGram);
    }

    /**
     * 살 때·팔 때를 함께 고시한다.
     *
     * @param sellPricePerGram   고객이 살 때(판매) 값. KRW/1g
     * @param buyPricePerGram    고객이 팔 때(매입) 값. KRW/1g
     * @param confirmLargeChange 확인 기준을 넘는 값이 있다는 것을 관리자가 확인했는가
     * @throws BusinessException 검증에 걸리거나 확인이 필요한데 받지 않았으면. 원본까지 아무것도 저장하지 않는다
     */
    @Transactional
    public QuotePublished declareBoth(MetalType metalType,
                                      BigDecimal sellPricePerGram,
                                      BigDecimal buyPricePerGram,
                                      String changeReason, String adminLoginId,
                                      boolean confirmLargeChange) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시세 변경 사유는 필수입니다.");
        }

        // 계산보다 잠금이 먼저다. 잠그기 전에 계산하면 그 사이 다른 고시가 끼어들어,
        // 확인 여부를 판단한 "지금 값"이 실제 지금 값과 달라진다.
        marketPriceService.lock(affectedBy(metalType));

        QuotePlan plan = marketPriceService.planDeclaration(metalType, sellPricePerGram, buyPricePerGram);
        marketPriceService.requireConfirmed(plan, confirmLargeChange);

        // 검증과 확인을 모두 통과한 뒤에야 원본을 남긴다. 거절된 입력까지 원본으로 쌓으면
        // "누가 무엇을 고시했나"를 보는 기록에 고시되지 않은 값이 섞인다.
        MarketPriceSource source = sourceRepository.findByCode(MarketPriceSource.CODE_MANUAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "MANUAL 시세 원천이 없습니다. V2 시드가 유실되었습니다."));

        OffsetDateTime issuedAt = OffsetDateTime.now();
        Map<PriceSide, Long> rawIds = new EnumMap<>(PriceSide.class);
        for (QuoteLine line : plan.lines()) {
            if (line.metalType() == metalType) {
                MarketPriceRaw raw = rawRepository.save(MarketPriceRaw.declared(
                        source, metalType, line.priceSide(), line.newPrice(), issuedAt, adminLoginId,
                        payload(line, adminLoginId)));
                rawIds.put(line.priceSide(), raw.getId());
            }
        }

        QuotePublished published = marketPriceService.publish(plan, rawIds, changeReason, adminLoginId, issuedAt);

        log.info("[MARKET-PRICE] 고시 — {} 살때={} 팔때={} 함께 발행 {}줄 확인필요={} by={}",
                metalType, sellPricePerGram, buyPricePerGram, plan.lines().size(),
                plan.needsConfirm(), adminLoginId);
        return published;
    }

    /** 이 금속을 고시하면 값이 바뀔 수 있는 금속. 24K 면 환산 금속까지다. */
    private static Set<MetalType> affectedBy(MetalType metalType) {
        Set<MetalType> metals = EnumSet.of(metalType);
        if (metalType == MarketPriceService.DERIVE_BASE) {
            metals.addAll(MarketPriceService.DERIVABLE);
        }
        return metals;
    }

    private String payload(QuoteLine line, String adminLoginId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", MarketPriceSource.CODE_MANUAL);
        payload.put("declaredBy", adminLoginId);
        payload.put("priceSide", line.priceSide().name());
        payload.put("pricePerGram", line.newPrice().value().toPlainString());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "원본 기록 직렬화 실패: " + e.getMessage());
        }
    }
}
