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
import java.time.OffsetDateTime;
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
     * 살 때·팔 때를 <b>한 번에</b> 고시한다.
     *
     * <h2>왜 한쪽씩 고시하면 안 되는가</h2>
     * 한 번에 한쪽만 낼 수 있으면, 살 때를 고시한 순간부터 팔 때를 고시하기까지의
     * 사이에 <b>살 때는 값이 있고 팔 때는 비어 있는 시세표</b>가 화면에 걸린다.
     * 관리자가 두 번째 입력을 잊거나, 그 사이에 자리를 뜨거나, 두 번째에서 오류가
     * 나면 그 상태가 그대로 남는다. 매입 문의를 받는 곳에서 매입가만 사라져 있는
     * 화면은 그 자체로 사고다.
     *
     * <p>그래서 두 값을 함께 받고, <b>한 트랜잭션</b>에서 처리한다. 한쪽이 실패하면
     * 둘 다 저장되지 않는다.
     *
     * <h2>한쪽이 변동률 임계를 넘으면</h2>
     * 둘 다 보류한다. 통과한 쪽만 내보내면 결국 같은 불일치가 생긴다 —
     * 이 메서드가 막으려던 바로 그 상태다.
     *
     * @param sellPricePerGram 고객이 살 때(판매) 기준 원본가. KRW/1g
     * @param buyPricePerGram  고객이 팔 때(매입) 기준 원본가. KRW/1g
     */
    @Transactional
    public PairedApplyResult declareBoth(MetalType metalType,
                                         BigDecimal sellPricePerGram,
                                         BigDecimal buyPricePerGram,
                                         String changeReason, String adminLoginId) {

        requireSpread(sellPricePerGram, buyPricePerGram);

        // 두 쪽이 <b>같은 발행 시각</b>을 갖게 한다. 각자 now() 를 찍으면 몇 밀리초가
        // 어긋나고, 이력 화면은 그 차이 때문에 한 번의 고시를 두 줄로 — 각각 한쪽이
        // 빈 채로 — 보여준다. 실제로 그렇게 나왔다.
        OffsetDateTime issuedAt = OffsetDateTime.now();

        PriceApplyResult sell = declare(metalType, PriceSide.SELL, sellPricePerGram,
                changeReason, adminLoginId, issuedAt);
        PriceApplyResult buy = declare(metalType, PriceSide.BUY, buyPricePerGram,
                changeReason, adminLoginId, issuedAt);

        // 한쪽만 발행된 상태로 끝내지 않는다. 예외를 던져 트랜잭션을 되돌리면 원본까지
        // 사라지므로, 이미 만들어진 발행을 되감는 대신 보류로 맞춘다.
        if (sell.applied() != buy.applied()) {
            throw new BusinessException(ErrorCode.PRICE_CHANGE_EXCEEDS_LIMIT,
                    ("%s 의 한쪽이 변동률 허용치를 넘었습니다 (살 때 %s / 팔 때 %s). "
                            + "한쪽만 고시되면 시세표가 어긋나므로 둘 다 발행하지 않았습니다. "
                            + "값을 다시 확인하거나, 승인 대기에서 처리해 주세요.")
                            .formatted(metalType,
                                    sell.applied() ? "정상" : "임계 초과",
                                    buy.applied() ? "정상" : "임계 초과"));
        }

        log.info("[MARKET-PRICE] 수동 고시(양쪽) — {} 살때={} 팔때={} 발행={} by={}",
                metalType, sellPricePerGram, buyPricePerGram, sell.applied(), adminLoginId);
        return new PairedApplyResult(sell, buy);
    }

    /**
     * 살 때는 팔 때보다 커야 한다.
     *
     * <p>고객이 사는 값이 파는 값보다 싸면 사고 되팔기만 해도 이익이 난다. 그 상태는
     * 오타 한 번으로 만들어지고(자릿수 하나), 발견은 손해가 난 뒤에 된다.
     *
     * <p>같은 값도 막는다. 매매 스프레드가 0 이면 거래할수록 비용만 나간다.
     */
    private void requireSpread(BigDecimal sell, BigDecimal buy) {
        if (sell == null || buy == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "살 때와 팔 때 시세를 모두 입력해 주세요.");
        }
        if (sell.compareTo(buy) <= 0) {
            throw new BusinessException(ErrorCode.PRICE_INVALID_VALUE,
                    ("살 때(%s원)가 팔 때(%s원)보다 크지 않습니다. "
                            + "고객이 사는 값이 파는 값보다 싸면 사고 되팔기만 해도 이익이 납니다. "
                            + "두 값이 바뀌지 않았는지 확인해 주세요.")
                            .formatted(sell.toPlainString(), buy.toPlainString()));
        }
    }

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
        return declare(metalType, priceSide, declaredPricePerGram, changeReason, adminLoginId, null);
    }

    /** 발행 시각을 지정하는 수동 고시. {@code issuedAt} 이 null 이면 지금 시각이다. */
    @Transactional
    public PriceApplyResult declare(MetalType metalType, PriceSide priceSide,
                                    BigDecimal declaredPricePerGram,
                                    String changeReason, String adminLoginId,
                                    OffsetDateTime issuedAt) {

        RawPrice rawPrice = toRawPrice(metalType, declaredPricePerGram, adminLoginId);
        validate(metalType, priceSide, rawPrice.price());

        MarketPriceSource source = sourceRepository.findByCode(MarketPriceSource.CODE_MANUAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "MANUAL 시세 소스가 없습니다. V2 시드가 유실되었습니다."));

        MarketPriceRaw raw = rawRepository.save(MarketPriceRaw.declared(
                source, metalType, rawPrice.price(), adminLoginId, rawPrice.payloadJson()));

        log.info("[MARKET-PRICE] 수동 고시 원본 저장 — {}/{} {}원/g rawId={} by={}",
                metalType, priceSide, rawPrice.price().value(), raw.getId(), adminLoginId);

        return marketPriceService.applyPrice(metalType, priceSide, raw.getId(), changeReason,
                adminLoginId, issuedAt);
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
