package com.hanseongge.domain.marketprice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.event.MarketPriceChangedEvent;
import com.hanseongge.domain.marketprice.repository.MarketPriceEffectiveRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.domain.setting.service.SystemSettingService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 시세 발행과 조회의 중심.
 *
 * <pre>
 * 관리자 입력 ──► 계획(QuotePlan) ──► [크게 바뀌면 확인] ──► 발행(effective)
 *                   └─ 24K 면 환산으로 설정된 18K·14K 도 같은 계획에 들어간다
 * </pre>
 *
 * <h2>이 클래스가 지키는 것</h2>
 * <ol>
 *   <li><b>발행은 덮어쓰지 않는다.</b> 기존 행을 마감하고 새 행을 쌓는다 (append-only)</li>
 *   <li><b>검증에 걸리면 아무것도 쓰지 않는다.</b> 지금 값이 그대로다 — 가격이 0 이 되는
 *       경로가 없다 (원칙 P6)</li>
 *   <li><b>한 번 고시한 값은 새 고시가 나올 때까지 유효하다.</b> 시간이 지났다고 내리지 않는다</li>
 *   <li><b>함께 바뀌는 것은 함께 바뀐다.</b> 살 때·팔 때, 그리고 24K 와 거기서 환산한 금속이
 *       한 트랜잭션에서 같은 발행 시각으로 나간다</li>
 * </ol>
 *
 * <h2>계획과 발행을 나눈 이유</h2>
 * 크게 바뀌는 값은 발행 전에 사람이 봐야 한다. 그러려면 "발행하면 무엇이 얼마로 바뀌는가"를
 * 쓰지 않고 계산할 수 있어야 하고, 실제 발행은 <b>그 계산을 그대로</b> 써야 한다. 확인창과
 * 발행이 따로 계산하면 둘이 어긋난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    /** 확인 기준 변동률(%) 설정 키. V1 이 5 로 시드한다. */
    public static final String SETTING_MAX_CHANGE_PERCENT = "MAX_PRICE_CHANGE_PERCENT";

    /** 환산의 기준 금속. */
    public static final MetalType DERIVE_BASE = MetalType.GOLD_24K;

    /** 24K 에서 환산할 수 있는 금속. 은·백금은 순금 값에 비율로 이어지지 않는다. */
    public static final Set<MetalType> DERIVABLE =
            Collections.unmodifiableSet(EnumSet.of(MetalType.GOLD_18K, MetalType.GOLD_14K));

    private static final BigDecimal DEFAULT_MAX_CHANGE_PERCENT = new BigDecimal("5");
    private static final int PERCENT_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    private final MarketPriceEffectiveRepository effectiveRepository;
    private final MarketPricePolicyRepository policyRepository;
    private final MarketPriceValidator validator;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // =================================================================
    // 계획 — 쓰지 않는다
    // =================================================================

    /**
     * 직접 고시의 계획.
     *
     * <p>트랜잭션은 호출하는 쪽이 연다. 발행 경로에서는 {@link #lock} 을 먼저 잡고 불러야
     * 한다 — 잠그기 전에 계산하면 그 사이 다른 고시가 끼어들어 "지금 값"이 틀린다.
     *
     * @throws BusinessException 환산으로 설정된 금속이거나, 값이 검증에 걸리면. 아무것도 쓰지 않는다
     */
    public QuotePlan planDeclaration(MetalType metalType, BigDecimal sellPricePerGram,
                                     BigDecimal buyPricePerGram) {
        if (findPolicy(metalType, PriceSide.SELL).isDerived()) {
            throw new BusinessException(ErrorCode.PRICE_METAL_DERIVED,
                    "%s 는 24K 환산으로 설정되어 있어 직접 고시할 수 없습니다.".formatted(metalType));
        }

        PricePerGram sell = requirePositive(metalType, PriceSide.SELL, sellPricePerGram);
        PricePerGram buy = requirePositive(metalType, PriceSide.BUY, buyPricePerGram);
        requireSpread(metalType, sell, buy);

        BigDecimal limit = limitPercent();
        List<QuoteLine> lines = new ArrayList<>();
        lines.add(directLine(metalType, PriceSide.SELL, sell, limit));
        lines.add(directLine(metalType, PriceSide.BUY, buy, limit));

        if (metalType == DERIVE_BASE) {
            for (MetalType target : DERIVABLE) {
                MarketPricePolicy policy = findPolicy(target, PriceSide.SELL);
                if (policy.isDerived()) {
                    lines.addAll(derivedPair(target, policy.getDeriveRatio(), sell, buy, limit));
                }
            }
        }
        return new QuotePlan(lines, limit);
    }

    /**
     * 환산 비율을 바꿨을 때의 계획 — 지금 24K 고시가에 새 비율을 곱한다.
     *
     * <p>24K 가 아직 고시되지 않았으면 빈 계획이다. 곱할 값이 없으니 설정만 저장되고,
     * 24K 를 처음 고시하는 순간 함께 나간다.
     */
    public QuotePlan planDerivation(MetalType target, BigDecimal ratio) {
        requireDerivable(target);
        requireRatio(ratio);

        BigDecimal limit = limitPercent();
        Optional<MarketPriceEffective> baseSell = current(DERIVE_BASE, PriceSide.SELL);
        Optional<MarketPriceEffective> baseBuy = current(DERIVE_BASE, PriceSide.BUY);
        if (baseSell.isEmpty() || baseBuy.isEmpty()) {
            return new QuotePlan(List.of(), limit);
        }
        return new QuotePlan(
                derivedPair(target, ratio, baseSell.get().price(), baseBuy.get().price(), limit),
                limit);
    }

    /**
     * 크게 바뀌는 줄이 있는데 확인을 받지 않았으면 거절한다. <b>아무것도 쓰기 전에</b> 부른다.
     *
     * <p>화면은 미리보기로 먼저 확인을 받으므로 보통은 여기서 걸리지 않는다. 미리보기와
     * 발행 사이에 다른 관리자가 값을 바꿨거나, 화면을 거치지 않고 API 를 부른 경우의 안전망이다.
     */
    public void requireConfirmed(QuotePlan plan, boolean confirmed) {
        if (confirmed || !plan.needsConfirm()) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        for (QuoteLine line : plan.lines()) {
            if (line.exceedsLimit()) {
                details.put(previousKey(line.metalType(), line.priceSide()),
                        "%s %s원 → %s원 (%s%%, 확인 기준 %s%%)".formatted(
                                label(line.metalType(), line.priceSide()),
                                MarketPriceValidator.won(line.currentPricePerGram()),
                                MarketPriceValidator.won(line.newPrice().value()),
                                signed(line.changePercent()),
                                plan.limitPercent().stripTrailingZeros().toPlainString()));
            }
        }
        throw new BusinessException(ErrorCode.PRICE_CHANGE_NEEDS_CONFIRM,
                "확인 없이 큰 변동을 고시하려 함: " + details.values(), details);
    }

    /**
     * (금속, side) 마다 트랜잭션 advisory lock 을 <b>정해진 순서로</b> 잡는다.
     *
     * <p>순서가 중요하다. 24K 고시는 18K·14K 까지, 18K 비율 변경은 24K 와 18K 를 잡는다.
     * 둘이 서로 다른 순서로 잡으면 서로를 기다리며 멈춘다. 금속 선언 순서 → 살 때 → 팔 때로
     * 고정한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Collection<MetalType> metals) {
        metals.stream().distinct().sorted().forEach(metal -> {
            for (PriceSide side : PriceSide.values()) {
                effectiveRepository.acquireLock(lockKey(metal, side));
            }
        });
    }

    // =================================================================
    // 발행
    // =================================================================

    /**
     * 계획대로 발행한다. 줄마다 기존 행을 마감하고 새 행을 쌓는다.
     *
     * @param baseRawIds side 별 근거 원본. 환산 줄도 같은 side 의 24K 원본을 가리킨다.
     *                   값이 없는 side 는 근거 원본 없이 발행된다
     * @param issuedAt   모든 줄이 함께 가질 발행 시각
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotePublished publish(QuotePlan plan, Map<PriceSide, Long> baseRawIds,
                                  String changeReason, String adminLoginId, OffsetDateTime issuedAt) {
        requireReason(changeReason);

        List<MarketPriceEffective> issued = new ArrayList<>();
        for (QuoteLine line : plan.lines()) {
            issued.add(issue(line, baseRawIds.get(line.priceSide()), changeReason, adminLoginId, issuedAt));
        }
        return new QuotePublished(plan, issued, issuedAt);
    }

    private MarketPriceEffective issue(QuoteLine line, Long baseRawId, String changeReason,
                                       String adminLoginId, OffsetDateTime issuedAt) {
        MarketPriceEffective current = current(line.metalType(), line.priceSide()).orElse(null);
        if (current != null) {
            current.close(issuedAt);
            // 반드시 먼저 내보낸다. JPA 는 INSERT 를 UPDATE 보다 앞세우므로, 이 flush 가
            // 없으면 신규 행(effective_to IS NULL)이 먼저 들어가 부분 UNIQUE 인덱스
            // ux_mpe_current 를 위반한다.
            effectiveRepository.saveAndFlush(current);
        }

        MarketPriceEffective issued = effectiveRepository.save(MarketPriceEffective.issue(
                line.metalType(), line.priceSide(), line.newPrice(), baseRawId,
                line.mode(), snapshot(line), issuedAt, changeReason, adminLoginId));

        BigDecimal previous = current == null ? null : current.getPricePerGram();

        auditLogService.record(AuditRecord.builder(AuditActions.PRICE_APPLY)
                .actor(ActorType.ADMIN, adminLoginId)
                .target(AuditActions.TARGET_MARKET_PRICE, previousKey(line.metalType(), line.priceSide()))
                .before(previous == null ? null : Map.of("pricePerGram", previous.toPlainString()))
                .after(auditAfter(line, issued, changeReason))
                .build());

        // AFTER_COMMIT 에서만 처리된다. 커밋 전에 캐시를 비우면 롤백된 시세가 캐시에 남는다.
        eventPublisher.publishEvent(new MarketPriceChangedEvent(
                line.metalType(), line.priceSide(), previous, issued.getPricePerGram(),
                issued.getId(), adminLoginId, issuedAt));

        log.info("[MARKET-PRICE] 시세 발행 — {}/{} {} → {} ({}{}, by={})",
                line.metalType(), line.priceSide(), previous, issued.getPricePerGram(),
                line.mode(), line.derived() ? " ×" + line.deriveRatio() : "", adminLoginId);
        return issued;
    }

    // =================================================================
    // 조회
    // =================================================================

    /**
     * 판매에 쓸 현재 시세. 고시된 적 없으면 {@link Optional#empty()} 다.
     *
     * <p><b>0 을 대신 돌려주지 않는다</b> — 호출부는 empty 를 받으면 "시세문의"를 표시하고
     * 주문을 막는다.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.MARKET_PRICES, key = "'cur:' + #metalType + ':' + #priceSide")
    public Optional<PricePerGram> getCurrentPrice(MetalType metalType, PriceSide priceSide) {
        return current(metalType, priceSide).map(MarketPriceEffective::price);
    }

    @Transactional(readOnly = true)
    public CurrentPriceView describeCurrent(MetalType metalType, PriceSide priceSide) {
        return current(metalType, priceSide)
                .map(CurrentPriceView::available)
                .orElseGet(() -> CurrentPriceView.none(metalType, priceSide));
    }

    /** 전 금속 × 살 때/팔 때. 고시된 적 없는 조합도 NONE 으로 채워 빠짐없이 돌려준다. */
    @Transactional(readOnly = true)
    public List<CurrentPriceView> describeAllCurrent() {
        Map<String, MarketPriceEffective> byKey = new LinkedHashMap<>();
        for (MarketPriceEffective e : effectiveRepository.findByEffectiveToIsNullOrderByMetalTypeAscPriceSideAsc()) {
            byKey.put(previousKey(e.getMetalType(), e.getPriceSide()), e);
        }

        List<CurrentPriceView> views = new ArrayList<>();
        for (MetalType metal : MetalType.values()) {
            for (PriceSide side : PriceSide.values()) {
                MarketPriceEffective effective = byKey.get(previousKey(metal, side));
                views.add(effective == null
                        ? CurrentPriceView.none(metal, side)
                        : CurrentPriceView.available(effective));
            }
        }
        return views;
    }

    /**
     * metal×side 별 <b>직전</b> 시세. 시세표의 변동폭 계산에 쓴다.
     *
     * <p>키는 {@code "GOLD_24K/SELL"} 형태다. 처음 고시라 직전 행이 없는 조합은 아예
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

    /** metal×side 키. 호출부도 이 메서드로 만든다. */
    public static String previousKey(MetalType metalType, PriceSide priceSide) {
        return metalType + "/" + priceSide;
    }

    @Transactional(readOnly = true)
    public Page<MarketPriceEffective> history(MetalType metalType, PriceSide priceSide, Pageable pageable) {
        return effectiveRepository
                .findByMetalTypeAndPriceSideOrderByEffectiveFromDescIdDesc(metalType, priceSide, pageable);
    }

    /**
     * 기간 이력 — 그 기간이 <b>시작할 때 적용 중이던 값</b>부터 준다. 오래된 순이다.
     *
     * <p>시세는 고시할 때만 한 행씩 생긴다. 기간 안의 행만 주면, 하루 한두 번 고시하는
     * 운영에서 "1일" 그래프는 점이 하나뿐이거나 아예 비어 버린다 — 그 사이에도 앞서 고시한
     * 값으로 계속 팔고 있었는데. 기간 직전에 발행된 행을 맨 앞에 붙이면 그 값이 기간
     * 첫머리부터 이어진다.
     *
     * <p>맨 앞 행의 발행 시각은 기간보다 앞선다. 발행 시각을 기간 시작으로 고쳐 내보내지
     * 않는다 — 그 시각에 고시한 것이 아니므로, 그렇게 적으면 이력이 거짓말을 한다.
     */
    @Transactional(readOnly = true)
    public List<MarketPriceEffective> historySince(MetalType metalType, PriceSide priceSide, int days) {
        OffsetDateTime from = OffsetDateTime.now().minusDays(days);

        List<MarketPriceEffective> rows = new ArrayList<>();
        effectiveRepository
                .findFirstByMetalTypeAndPriceSideAndEffectiveFromLessThanOrderByEffectiveFromDescIdDesc(
                        metalType, priceSide, from)
                .ifPresent(rows::add);
        rows.addAll(effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveFromGreaterThanEqualOrderByEffectiveFromAsc(
                        metalType, priceSide, from));
        return rows;
    }

    @Transactional(readOnly = true)
    public Optional<MarketPriceEffective> findCurrent(MetalType metalType, PriceSide priceSide) {
        return current(metalType, priceSide);
    }

    /**
     * 시세 버전 — 가장 최근에 발행된 시세 행의 번호. 고시된 적 없으면 0.
     *
     * <p>열어 둔 쇼핑몰 화면이 "시세가 바뀌었나"만 묻는 데 쓴다. 시세표 전체를 주기적으로
     * 받게 하면 방문자 수만큼 시세·직전 시세 조회가 돈다. 이 번호는 고시할 때마다 커지고
     * 그 외에는 변하지 않으므로, 화면은 숫자 하나를 비교해 바뀌었을 때만 다시 그린다.
     *
     * <p>캐시된다. 발행 이벤트가 커밋 뒤에 {@code MARKET_PRICES} 를 통째로 비우므로 고시 직후
     * 첫 조회부터 새 번호다. 그 사이의 조회는 DB 에 닿지 않는다.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.MARKET_PRICES, key = "'version'")
    public long currentVersion() {
        Long latest = effectiveRepository.findLatestIssuedId();
        return latest == null ? 0L : latest;
    }

    // =================================================================
    // 검증 — 정책 서비스도 같은 규칙을 쓴다
    // =================================================================

    /** 환산할 수 있는 금속인가. */
    public static void requireDerivable(MetalType metal) {
        if (metal == null || !DERIVABLE.contains(metal)) {
            String message = "24K 환산은 18K·14K 만 설정할 수 있습니다.";
            throw new BusinessException(ErrorCode.INVALID_INPUT, message + " 요청=" + metal,
                    Map.of("metalType", message));
        }
    }

    /**
     * 환산 비율 검사. 0 초과 1 이하, 소수 넷째 자리까지.
     *
     * <p>1 을 넘으면 18K 가 순금보다 비싸진다 — 오타(7.35) 말고는 그런 값이 들어올 이유가
     * 없다. 반대로 너무 작은 오타(0.0735)는 여기서 못 잡고, 환산값이 금속 하한에 걸려 막힌다.
     */
    public static void requireRatio(BigDecimal ratio) {
        String problem = null;
        if (ratio == null) {
            problem = "환산 비율을 입력해 주세요. 예: 0.735";
        } else if (ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            problem = "환산 비율은 0보다 크고 1 이하여야 합니다. 예: 0.735";
        } else if (ratio.stripTrailingZeros().scale() > RATIO_SCALE) {
            problem = "환산 비율은 소수 넷째 자리까지 입력할 수 있습니다.";
        }
        if (problem != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, problem + " 요청=" + ratio,
                    Map.of("ratio", problem));
        }
    }

    /** 검사를 통과한 비율을 DB 자릿수(소수 넷째)로 맞춘다. 반올림이 일어나지 않는다. */
    public static BigDecimal normalizeRatio(BigDecimal ratio) {
        return ratio.setScale(RATIO_SCALE, RoundingMode.UNNECESSARY);
    }

    // =================================================================
    // 내부
    // =================================================================

    private Optional<MarketPriceEffective> current(MetalType metalType, PriceSide priceSide) {
        return effectiveRepository.findByMetalTypeAndPriceSideAndEffectiveToIsNull(metalType, priceSide);
    }

    private QuoteLine directLine(MetalType metal, PriceSide side, PricePerGram price, BigDecimal limit) {
        MarketPriceEffective current = current(metal, side).orElse(null);
        PriceValidation validation = validator.validate(metal, label(metal, side), price,
                current == null ? null : current.price());
        if (!validation.valid()) {
            throw invalid(metal, side, validation.reason());
        }
        return line(metal, side, price, current, limit, PolicyMode.DIRECT, null, null);
    }

    private List<QuoteLine> derivedPair(MetalType target, BigDecimal ratio,
                                        PricePerGram baseSell, PricePerGram baseBuy, BigDecimal limit) {
        PricePerGram sell = derive(target, PriceSide.SELL, baseSell, ratio);
        PricePerGram buy = derive(target, PriceSide.BUY, baseBuy, ratio);
        requireSpread(target, sell, buy);

        return List.of(
                line(target, PriceSide.SELL, sell, current(target, PriceSide.SELL).orElse(null),
                        limit, PolicyMode.DERIVED, ratio, baseSell.value()),
                line(target, PriceSide.BUY, buy, current(target, PriceSide.BUY).orElse(null),
                        limit, PolicyMode.DERIVED, ratio, baseBuy.value()));
    }

    /**
     * 환산값 한쪽.
     *
     * <p>범위만 본다. 단위 오입력(3.75배) 검사는 사람이 친 숫자를 위한 것이고, 환산값은
     * 이미 검증된 24K 에 1 이하 비율을 곱한 것이다. 대신 비율 오타(0.0735)는 금속 하한에서 걸린다.
     */
    private PricePerGram derive(MetalType target, PriceSide side, PricePerGram base, BigDecimal ratio) {
        BigDecimal value = DerivedPrice.of(base.value(), ratio, side);
        String basis = "24K %s원 × %s".formatted(MarketPriceValidator.won(base.value()),
                ratio.stripTrailingZeros().toPlainString());
        if (value.signum() <= 0) {
            throw invalid(target, side,
                    "%s 환산값이 0원 이하입니다 (%s).".formatted(label(target, side), basis));
        }
        PricePerGram price = PricePerGram.of(value);
        PriceValidation validation = validator.validate(target, label(target, side), price, null);
        if (!validation.valid()) {
            throw invalid(target, side, "%s (%s 로 계산한 값입니다. 환산 비율을 확인해 주세요.)"
                    .formatted(validation.reason(), basis));
        }
        return price;
    }

    private QuoteLine line(MetalType metal, PriceSide side, PricePerGram price,
                           MarketPriceEffective current, BigDecimal limit,
                           PolicyMode mode, BigDecimal ratio, BigDecimal base) {
        BigDecimal percent = current == null ? null : price.changePercentFrom(current.price());
        boolean exceeds = percent != null && percent.abs().compareTo(limit) > 0;
        return new QuoteLine(metal, side, price,
                current == null ? null : current.getPricePerGram(),
                percent == null ? null : percent.setScale(PERCENT_SCALE, RoundingMode.HALF_UP),
                exceeds, mode, ratio, base);
    }

    private PricePerGram requirePositive(MetalType metal, PriceSide side, BigDecimal value) {
        if (value == null) {
            throw invalid(metal, side, "%s 시세를 입력해 주세요.".formatted(label(metal, side)));
        }
        try {
            return PricePerGram.of(value);
        } catch (IllegalArgumentException e) {
            throw invalid(metal, side, "%s 시세는 0보다 커야 합니다.".formatted(label(metal, side)));
        }
    }

    /**
     * 살 때는 팔 때보다 커야 한다.
     *
     * <p>고객이 사는 값이 파는 값보다 싸면 사고 되팔기만 해도 이익이 난다. 그 상태는
     * 오타 한 번으로 만들어지고(자릿수 하나), 발견은 손해가 난 뒤에 된다. 같은 값도 막는다 —
     * 스프레드가 0 이면 거래할수록 비용만 나간다.
     */
    private void requireSpread(MetalType metal, PricePerGram sell, PricePerGram buy) {
        if (sell.compareTo(buy) <= 0) {
            String message = "%s 살 때 %s원이 팔 때 %s원보다 크지 않습니다. 두 값이 바뀌지 않았는지 확인해 주세요."
                    .formatted(metal.displayName(), MarketPriceValidator.won(sell.value()),
                            MarketPriceValidator.won(buy.value()));
            throw new BusinessException(ErrorCode.PRICE_SPREAD_INVERTED, message,
                    Map.of(metal.name(), message));
        }
    }

    private static BusinessException invalid(MetalType metal, PriceSide side, String message) {
        return new BusinessException(ErrorCode.PRICE_INVALID_VALUE, message,
                Map.of(previousKey(metal, side), message));
    }

    private MarketPricePolicy findPolicy(MetalType metalType, PriceSide priceSide) {
        return policyRepository.findByMetalTypeAndPriceSide(metalType, priceSide)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_POLICY_NOT_FOUND,
                        "정책 없음: " + metalType + "/" + priceSide));
    }

    private BigDecimal limitPercent() {
        return systemSettingService.getDecimal(SETTING_MAX_CHANGE_PERCENT, DEFAULT_MAX_CHANGE_PERCENT);
    }

    private void requireReason(String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시세 변경 사유는 필수입니다.");
        }
    }

    /** 사유·오류 문구에 쓰는 이름. 화면의 말과 같아야 한다. */
    static String label(MetalType metal, PriceSide side) {
        return metal.displayName() + (side == PriceSide.SELL ? " 살 때" : " 팔 때");
    }

    private static String signed(BigDecimal percent) {
        return (percent.signum() > 0 ? "+" : "") + percent.toPlainString();
    }

    /** advisory lock 키. metal×side 조합마다 고유해야 한다. */
    private static String lockKey(MetalType metalType, PriceSide priceSide) {
        return "market_price:" + metalType.name() + ":" + priceSide.name();
    }

    /**
     * 발행 근거를 통째로 담는다. 설정이 나중에 바뀌어도 "이 시세가 어떻게 나왔나"가 남는다.
     * 환산이면 곱한 24K 값과 비율까지 넣는다 — 그 둘이 없으면 18K 값을 되짚을 수 없다.
     */
    private String snapshot(QuoteLine line) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", line.mode().name());
        snapshot.put("pricePerGram", line.newPrice().value());
        if (line.derived()) {
            snapshot.put("deriveFrom", DERIVE_BASE.name());
            snapshot.put("deriveRatio", line.deriveRatio());
            snapshot.put("basePricePerGram", line.basePricePerGram());
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            // 스냅샷을 못 만들면 발행하지 않는다. 근거 없는 시세를 남기느니 실패가 낫다.
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "시세 스냅샷 직렬화 실패: " + e.getMessage());
        }
    }

    private Map<String, Object> auditAfter(QuoteLine line, MarketPriceEffective issued, String changeReason) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("effectiveId", issued.getId());
        after.put("pricePerGram", issued.getPricePerGram().toPlainString());
        after.put("policyMode", line.mode().name());
        if (line.derived()) {
            after.put("deriveRatio", line.deriveRatio().toPlainString());
            after.put("basePricePerGram", line.basePricePerGram().toPlainString());
        }
        after.put("changePercent", line.changePercent() == null ? null : line.changePercent().toPlainString());
        after.put("reason", changeReason);
        return after;
    }
}
