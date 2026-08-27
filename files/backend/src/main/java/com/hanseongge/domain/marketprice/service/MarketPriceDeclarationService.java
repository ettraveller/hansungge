package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.provider.ManualMarketPriceProvider;
import com.hanseongge.domain.marketprice.provider.RawPrice;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceSourceRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PricePerGram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 관리자 수동 고시 (개발계획 §12.3 — 결함 D-5 해결).
 *
 * <h2>왜 원본을 먼저 만드는가</h2>
 * 관리자가 입력한 최종가를 {@code effective} 에 바로 쓰면 <b>원본 레이어를 건너뛴다.</b>
 * 그러면 "원본이 얼마였나"가 남지 않는데, 수동 고시가 <b>주력 경로</b>이므로 그 결함이
 * 시스템 대부분의 시세에 적용된다. 그래서 수동도 자동과 똑같이
 * <b>L1(raw) → L2(policy) → L3(effective)</b> 를 통과한다.
 *
 * <p>관리자가 시세를 몇 번 바꾸든 입력 이력이 전부 {@code market_price_raw} 에 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceDeclarationService {

    private final ManualMarketPriceProvider manualProvider;
    private final MarketPriceSourceRepository sourceRepository;
    private final MarketPriceRawRepository rawRepository;
    private final MarketPriceValidator validator;
    private final MarketPriceService marketPriceService;

    /**
     * 수동 고시 — 원본 저장 + 정책 적용 + 발행까지 한 트랜잭션.
     *
     * <p>변동률이 임계값을 넘으면 <b>원본만 남고 발행은 보류</b>된다. 원본을 되돌리지 않는
     * 이유는, 관리자가 실제로 그 값을 입력했다는 사실 자체가 기록되어야 하기 때문이다.
     *
     * @param declaredPricePerGram 관리자 입력 원본가 (KRW/1g)
     * @param changeReason         필수
     */
    @Transactional
    public PriceApplyResult declare(MetalType metalType, PriceSide priceSide,
                                    BigDecimal declaredPricePerGram,
                                    String changeReason, String adminLoginId) {

        RawPrice rawPrice = toRawPrice(metalType, declaredPricePerGram, adminLoginId);
        validate(metalType, priceSide, rawPrice.price());

        MarketPriceSource source = sourceRepository.findByCode(MarketPriceSource.CODE_MANUAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "MANUAL 시세 소스가 없습니다. V2 시드가 유실되었습니다."));

        MarketPriceRaw raw = rawRepository.save(MarketPriceRaw.declared(
                source, metalType, rawPrice.price(), adminLoginId, rawPrice.payloadJson()));

        log.info("[MARKET-PRICE] 수동 고시 원본 저장 — {}/{} {}원/g rawId={} by={}",
                metalType, priceSide, rawPrice.price().value(), raw.getId(), adminLoginId);

        return marketPriceService.applyPrice(metalType, priceSide, raw.getId(), changeReason, adminLoginId);
    }

    /**
     * 0 이하 입력을 도메인 오류로 바꾼다.
     *
     * <p>{@link PricePerGram} 이 던지는 것은 {@link IllegalArgumentException} 이라 그대로 두면
     * 500 이 나간다. 관리자 입력 오류는 400 이어야 한다.
     */
    private RawPrice toRawPrice(MetalType metalType, BigDecimal declaredPricePerGram, String adminLoginId) {
        if (declaredPricePerGram == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시세를 입력해 주세요.");
        }
        try {
            return manualProvider.declare(metalType, declaredPricePerGram, adminLoginId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PRICE_INVALID_VALUE, e.getMessage());
        }
    }

    /**
     * 범위·단위 검증.
     *
     * <p>실패를 {@code market_price_fetch_logs} 에 남기지 않는 이유: 그 테이블은 <b>수집</b>
     * 이력이고 대시보드의 "마지막 성공 시각"이 거기서 나온다. 관리자 오타를 수집 실패로
     * 섞으면 자동 수집 상태 판단이 흐려진다. 관리자 행위는 {@code audit_logs} 가 받는다.
     */
    private void validate(MetalType metalType, PriceSide priceSide, PricePerGram price) {
        Optional<PricePerGram> previous = marketPriceService.findLatestPriceForValidation(metalType, priceSide);
        PriceValidation validation = validator.validate(metalType, price, previous.orElse(null));
        if (!validation.valid()) {
            log.warn("[MARKET-PRICE] 수동 고시 값 거부 — {}", validation.reason());
            throw new BusinessException(ErrorCode.PRICE_INVALID_VALUE, validation.reason());
        }
    }
}
