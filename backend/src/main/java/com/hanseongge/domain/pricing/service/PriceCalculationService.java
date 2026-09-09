package com.hanseongge.domain.pricing.service;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
import com.hanseongge.domain.marketprice.service.PriceBreakdown;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.entity.ProductStatus;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.global.util.PriceRoundingResolver;
import com.hanseongge.global.util.RoundingPolicy;
import com.hanseongge.global.util.VatMode;
import com.hanseongge.global.util.VatPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 상품 가격 계산 엔진 (설계서 §7, 개발계획 §13).
 *
 * <h2>이 프로젝트의 유일한 가격 산출 지점</h2>
 * 상품 목록·상세·장바구니·주문 생성이 <b>전부 이 클래스를 통과</b>한다. 화면마다 따로
 * 계산하면 어느 화면이 맞는지 알 수 없게 되고, 그 불일치는 결제 금액이 어긋나는 사고로
 * 이어진다. 프론트엔드가 보낸 금액은 어떤 경우에도 쓰지 않는다 (코딩규칙 P1).
 *
 * <h2>파이프라인 (개발계획 §13.5)</h2>
 * <pre>
 * 1. 상품·옵션 로드 → 판매 가능 여부 확인
 * 2. 가격 모델 정규화 (SIMPLE → 구성요소)
 * 3. METAL_BASE: 최종 시세 조회
 *      없음 / 노후 → INQUIRY_ONLY 반환하고 종료   (예외 아님, 0원 아님)
 * 4. sort 순으로 구성요소 누적 → 소계
 * 5. (할인·쿠폰 — STEP 11 장바구니/주문에서 추가)
 * 6. 부가세 정책 적용   [A-1 미확정 → 임시 NONE + WARN]
 * 7. 반올림 정책 적용   [A-2 확정 = CEIL_100]  ← 마지막에 단 한 번
 * 8. PriceQuote 반환
 * </pre>
 *
 * <h2>반올림은 마지막 한 번뿐이다</h2>
 * 단계마다 반올림하면 <b>구성요소 합계와 최종가가 어긋나</b> 주문 명세서가 맞지 않는다.
 * 각 구성요소는 전(錢) 단위({@link Money} scale 2)로 확정되고, 그 합이 정확히 소계가 되며,
 * 원/백원 단위 반올림은 {@link RoundingPolicy} 가 마지막에 한 번만 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCalculationService {

    /** 판매가는 항상 판매(SELL) 시세로 계산한다. 매입(BUY)은 시세표 게시 전용이다. */
    private static final PriceSide SALE_SIDE = PriceSide.SELL;

    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final ProductComponentNormalizer normalizer;
    private final MarketPriceService marketPriceService;
    private final com.hanseongge.domain.marketprice.service.AutoQuoteService autoQuoteService;
    private final PriceRoundingResolver roundingResolver;
    private final VatPolicyResolver vatPolicyResolver;

    /**
     * 현재 시세 기준 가격 산출 (상품 조회·장바구니용).
     *
     * <p>캐시된다. 시세가 바뀌면 {@code MarketPriceChangedEvent} 가 전체를 무효화하므로
     * (개발계획 §12.7) 다음 조회부터 새 시세가 자동 반영된다 — <b>상품 테이블을 일괄
     * UPDATE 하는 배치는 존재하지 않는다.</b> TTL 60초는 이벤트 유실 시의 안전망이다.
     *
     * @param optionId null 이면 옵션을 쓰지 않는 상품이거나 대표가 산출이다
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PRODUCT_PRICES, key = "'q:' + #productId + ':' + #optionId")
    public PriceQuote quote(Long productId, Long optionId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "상품 없음: " + productId));

        ProductOption option = loadOption(product, optionId);
        return calculate(product, option);
    }

    /**
     * 관리자 미리보기 — 숨김·판매종료 상품도 계산한다.
     *
     * <p>운영자는 <b>공개 전에</b> 가격을 확인해야 한다. 노출 상태 검사를 그대로 적용하면
     * 등록 직후(HIDDEN) 상품의 가격을 볼 방법이 없어, 결국 임시로 공개했다가 되돌리는
     * 위험한 절차가 생긴다. 캐시도 태우지 않는다 — 미리보기는 항상 지금 값이어야 한다.
     */
    @Transactional(readOnly = true)
    public PriceQuote preview(Long productId, Long optionId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "상품 없음: " + productId));

        ProductOption option = loadOption(product, optionId);
        return compute(product, option, true);
    }

    // =================================================================
    // 계산
    // =================================================================

    private PriceQuote calculate(Product product, ProductOption option) {
        return compute(product, option, false);
    }

    private PriceQuote compute(Product product, ProductOption option, boolean adminPreview) {
        Long productId = product.getId();
        Long optionId = option == null ? null : option.getId();

        // 1. 노출·판매 가능 여부
        if (!adminPreview && !product.isVisible()) {
            return PriceQuote.unavailable(PriceStatus.NOT_FOR_SALE, productId, optionId,
                    "판매하지 않는 상품입니다.");
        }

        // 2~3. 시세 연동 상품이면 최종 시세를 먼저 확보한다.
        //      없거나 노후면 여기서 끝난다 — 0원을 만들 기회 자체를 주지 않는다.
        MarketContext market = resolveMarket(product);
        if (market.inquiryOnly()) {
            return PriceQuote.unavailable(PriceStatus.INQUIRY_ONLY, productId, optionId,
                    market.reason());
        }

        // 4. 구성요소 정규화 + 누적
        List<ComponentLine> components = normalizer.normalize(product, option, market.price());
        Money subtotal = sum(components);

        // 5. 할인·쿠폰은 주문 시점(STEP 11~13)에 붙는다. 조회 단계에서는 적용하지 않는다.

        // 6. 부가세
        VatMode vatMode = vatPolicyResolver.resolve();
        Money vat = vatMode.vatOf(subtotal, vatPolicyResolver.ratePercent());
        Money withVat = vatMode.totalOf(subtotal, vat);

        // 7. 반올림 — 마지막에 단 한 번
        RoundingPolicy rounding = roundingResolver.resolve();
        Money finalUnitPrice = withVat.applyRounding(rounding);

        // 8. 재고 판단은 금액 산출 뒤에 한다. 품절이어도 가격은 보여줘야 하기 때문이다.
        PriceStatus status = resolveStatus(product, option, adminPreview);

        return new PriceQuote(
                status, productId, optionId,
                market.price(), market.rawPrice(), market.adjustment(),
                market.effectiveId(), market.quotedAt(),
                components, subtotal,
                vatMode, vatPolicyResolver.isUsingTemporaryDefault(), vat,
                rounding, roundingResolver.isUsingTemporaryDefault(), finalUnitPrice,
                status.isOrderable() ? null : statusReason(status));
    }

    /**
     * 구성요소 합계.
     *
     * <p>각 줄이 이미 전 단위로 확정된 {@link Money} 라서, 더한 값이 <b>정확히</b> 소계가 된다.
     * 누적 중간값을 더 높은 정밀도로 들고 가다 마지막에 자르면 줄 합계와 소계가 1원씩
     * 어긋날 수 있고, 그 차이는 주문 명세서에서 그대로 드러난다.
     */
    private Money sum(List<ComponentLine> components) {
        return components.stream()
                .sorted((a, b) -> Integer.compare(a.sort(), b.sort()))
                .map(ComponentLine::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    /** 시세 연동 상품의 시세 확보. 미연동 상품이면 빈 컨텍스트다. */
    private MarketContext resolveMarket(Product product) {
        // 값을 내걸지 않기로 <b>정한</b> 상품. 시세가 멀쩡해도 값을 만들지 않는다.
        //
        // 시세가 끊겨서 물러나는 시세문의와 문구를 달리한다. 둘을 같은 말로 두면
        // 화면만 보고는 "장애"와 "원래 그런 상품"을 구분할 수 없고, 그러면 앞의
        // 장애를 뒤의 정상으로 착각하고 지나치게 된다.
        if (product.getPriceType() == PriceType.INQUIRY) {
            return MarketContext.inquiryOnly("가격은 문의로 안내해 드립니다.");
        }
        if (!product.getPriceType().isMarketLinked()) {
            return MarketContext.notLinked();
        }
        if (product.getMetalType() == null) {
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "시세 연동 상품에 metal_type 이 없습니다: productId=" + product.getId());
        }

        // 전역 차단기. 상품이 MARKET_* 로 설정돼 있어도, 운영이 허용한 금속이 아니면
        // 시세를 따르지 않는다.
        //
        // 상품마다 price_type 이 있는데 왜 또 막는가 — 그것은 상품 한 건의 설정이고
        // 이것은 범위 전체의 결정이다. "지금은 순금만 연동한다"를 상품 수백 건의
        // price_type 을 일일이 되돌리지 않고 한 줄로 정할 수 있어야 하고, 되돌릴 때도
        // 마찬가지여야 한다.
        if (!autoQuoteService.linkedMetals().contains(product.getMetalType())) {
            log.debug("[PRICING] 연동 대상 금속이 아님 — productId={} metal={}",
                    product.getId(), product.getMetalType());
            return MarketContext.inquiryOnly("시세문의 후 주문해 주세요.");
        }

        CurrentPriceView view = marketPriceService.describeCurrent(product.getMetalType(), SALE_SIDE);
        if (!view.isSellable()) {
            log.debug("[PRICING] 시세문의 — productId={} metal={} 사유={}",
                    product.getId(), product.getMetalType(), view.availability());
            return MarketContext.inquiryOnly(switch (view.availability()) {
                case NONE -> "현재 시세가 고시되지 않았습니다. 시세문의 후 주문해 주세요.";
                case STALE -> "시세 정보가 오래되었습니다. 시세문의 후 주문해 주세요.";
                case AVAILABLE -> "";
            });
        }

        MarketPriceEffective effective = view.effective();
        PriceBreakdown breakdown = marketPriceService.breakdown(effective);
        return new MarketContext(
                effective.price(),
                breakdown.rawPricePerGram() == null ? null : PricePerGram.of(breakdown.rawPricePerGram()),
                breakdown.adjustment(),
                effective.getId(),
                effective.getEffectiveFrom(),
                null);
    }

    /**
     * 재고·판매 상태 판정.
     *
     * <p>옵션이 있는 상품은 <b>선택된 옵션의 재고</b>가 기준이다. 옵션을 지정하지 않은
     * 대표가 산출에서는 재고를 보지 않는다 — 목록 화면에서 어떤 옵션이든 하나라도 살 수
     * 있으면 "품절"로 표시하면 안 되기 때문이다. 주문 시점에는 옵션이 반드시 지정되므로
     * 그때 다시 판정된다.
     */
    private PriceStatus resolveStatus(Product product, ProductOption option, boolean adminPreview) {
        if (adminPreview && !product.isOnSale()) {
            return PriceStatus.NOT_FOR_SALE;
        }
        if (!product.isOnSale()) {
            return product.getStatus() == ProductStatus.SOLD_OUT
                    ? PriceStatus.SOLD_OUT
                    : PriceStatus.NOT_FOR_SALE;
        }
        if (option != null && !option.isInStock()) {
            return PriceStatus.SOLD_OUT;
        }
        return PriceStatus.AVAILABLE;
    }

    private String statusReason(PriceStatus status) {
        return switch (status) {
            case SOLD_OUT -> "품절입니다.";
            case NOT_FOR_SALE -> "판매하지 않는 상품입니다.";
            case INQUIRY_ONLY -> "시세문의 후 주문해 주세요.";
            case AVAILABLE -> null;
        };
    }

    private ProductOption loadOption(Product product, Long optionId) {
        if (optionId == null) {
            return null;
        }
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "옵션 없음: " + optionId));
        if (!option.getProductId().equals(product.getId())) {
            // 다른 상품의 옵션 ID 를 끼워 넣어 가격을 왜곡하는 경로를 막는다.
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "옵션이 상품에 속하지 않습니다: productId=%d optionId=%d"
                            .formatted(product.getId(), optionId));
        }
        return option;
    }

    /** 시세 조회 결과를 한 덩어리로 나른다. */
    private record MarketContext(
            PricePerGram price,
            PricePerGram rawPrice,
            java.math.BigDecimal adjustment,
            Long effectiveId,
            java.time.OffsetDateTime quotedAt,
            String reason
    ) {

        static MarketContext notLinked() {
            return new MarketContext(null, null, null, null, null, null);
        }

        static MarketContext inquiryOnly(String reason) {
            return new MarketContext(null, null, null, null, null, reason);
        }

        boolean inquiryOnly() {
            return reason != null;
        }
    }

    /** 옵션 목록. 상세 화면이 옵션별 가격을 한 번에 그릴 때 쓴다. */
    @Transactional(readOnly = true)
    public List<PriceQuote> quoteAllOptions(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "상품 없음: " + productId));

        List<ProductOption> options = optionRepository.findByProductIdAndActiveTrueOrderBySortAscIdAsc(productId);
        if (options.isEmpty()) {
            return List.of(calculate(product, null));
        }
        return options.stream().map(option -> calculate(product, option)).toList();
    }

    /** 대표가 — 옵션이 있으면 가장 싼 옵션. 목록 화면의 "○○원~" 표시에 쓴다. */
    @Transactional(readOnly = true)
    public Optional<PriceQuote> representativeQuote(Long productId) {
        return quoteAllOptions(productId).stream()
                .filter(q -> q.status().hasPrice())
                .min((a, b) -> a.finalUnitPrice().compareTo(b.finalUnitPrice()));
    }
}
