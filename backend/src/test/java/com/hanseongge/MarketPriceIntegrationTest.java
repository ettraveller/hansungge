package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.domain.marketprice.dto.PublicPriceResponse;
import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.entity.RawStatus;
import com.hanseongge.domain.marketprice.provider.MarketPriceFetchException;
import com.hanseongge.domain.marketprice.provider.MarketPriceProvider;
import com.hanseongge.domain.marketprice.provider.RawPrice;
import com.hanseongge.domain.marketprice.repository.MarketPriceEffectiveRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceFetchLogRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePendingRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceSourceRepository;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import com.hanseongge.domain.marketprice.service.MarketPriceCollectionWriter;
import com.hanseongge.domain.marketprice.service.MarketPriceCollector;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.marketprice.service.MarketPricePendingService;
import com.hanseongge.domain.marketprice.service.MarketPricePolicyService;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
import com.hanseongge.domain.marketprice.service.PriceApplyResult;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STEP 6 완료 검증 — 시세 3층 구조 + 안전장치 (개발계획 §16.3 시나리오).
 *
 * <p>고정하는 시나리오:
 * <ul>
 *   <li><b>S1</b> 원본 200,000 + 보정 +10,000 → 최종 210,000, <b>원본은 그대로</b></li>
 *   <li><b>S2</b> 시세 변경 → 신규 행 발행 + 이전 행 {@code effective_to} 마감 (이력 보존)</li>
 *   <li><b>S4</b> 수집 실패 → {@code fetch_logs} 기록, <b>effective 불변</b>, 0원 아님</li>
 *   <li><b>S5</b> 변동률 임계값 초과 → {@code pending} 등록, <b>effective 미발행</b>, 승인 시에만 적용</li>
 *   <li><b>S6</b> 유효 시세 없음 / 노후 → "시세문의" (500 아님)</li>
 * </ul>
 *
 * <p>실제 PostgreSQL 위에서 돈다. append-only 트리거·부분 UNIQUE 인덱스·advisory lock 이
 * <b>실제로 동작하는지</b>가 이 STEP 검증의 핵심이라 H2 로는 대체할 수 없다.
 */
@SpringBootTest
class MarketPriceIntegrationTest extends EmbeddedPostgresSupport {

    private static final String ADMIN = "test_price_admin";

    @Autowired MarketPriceService marketPriceService;
    @Autowired MarketPriceDeclarationService declarationService;
    @Autowired MarketPricePolicyService policyService;
    @Autowired MarketPricePendingService pendingService;
    @Autowired MarketPriceCollector collector;
    @Autowired MarketPriceCollectionWriter collectionWriter;

    @Autowired MarketPriceEffectiveRepository effectiveRepository;
    @Autowired MarketPriceRawRepository rawRepository;
    @Autowired MarketPricePolicyRepository policyRepository;
    @Autowired MarketPricePendingRepository pendingRepository;
    @Autowired MarketPriceFetchLogRepository fetchLogRepository;
    @Autowired MarketPriceSourceRepository sourceRepository;
    @Autowired SystemSettingService systemSettingService;
    @Autowired JdbcTemplate jdbcTemplate;

    /**
     * 시세 테이블 전체를 비우고 정책을 시드 상태로 되돌린다.
     *
     * <p>{@code market_prices_effective} 는 DELETE 트리거가 막으므로 트리거를 잠시 끈다.
     * <b>테스트에서만</b> 하는 일이다 — 애플리케이션 코드에는 이 테이블을 지우는 경로가 없다.
     */
    @BeforeEach
    @AfterEach
    void resetMarketPriceTables() {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        jdbcTemplate.execute("ALTER TABLE market_price_fetch_logs DISABLE TRIGGER trg_mpfl_guard");
        try {
            jdbcTemplate.update("DELETE FROM market_prices_effective");
            jdbcTemplate.update("DELETE FROM market_price_pending");
            jdbcTemplate.update("DELETE FROM market_price_fetch_logs");
            jdbcTemplate.update("DELETE FROM market_price_raw");
            jdbcTemplate.update(
                    "UPDATE market_price_policies SET mode='MANUAL', adj_amount=0, adj_percent=0, "
                            + "manual_price=NULL, updated_by=NULL");
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
            jdbcTemplate.execute("ALTER TABLE market_price_fetch_logs ENABLE TRIGGER trg_mpfl_guard");
        }
    }

    // =================================================================
    // S1 — 원본 + 보정 → 최종. 원본은 그대로 남는다
    // =================================================================

    @Nested
    @DisplayName("S1 — 원본과 보정 시세의 분리")
    class RawAndAdjustment {

        @Test
        @DisplayName("원본 200,000 + 보정 +10,000 → 최종 210,000. 원본 행은 200,000 그대로 남는다")
        void rawIsNeverOverwritten() {
            setPolicy(PolicyMode.FIXED_ADJ, new BigDecimal("10000"), null, null);

            PriceApplyResult result = declare("200000", "요구사항 예시 검증");

            assertThat(result.applied()).isTrue();
            assertThat(result.calculation().raw().value()).isEqualByComparingTo("200000.00");
            assertThat(result.calculation().adjustment()).isEqualByComparingTo("10000.00");
            assertThat(result.effective().getPricePerGram()).isEqualByComparingTo("210000.00");

            // 원본 테이블에는 보정되지 않은 값이 남아야 한다
            MarketPriceRaw raw = rawRepository.findById(result.effective().getBaseRawId()).orElseThrow();
            assertThat(raw.getPricePerGram()).isEqualByComparingTo("200000.00");
            assertThat(raw.getStatus()).isEqualTo(RawStatus.NORMAL);
            assertThat(raw.getCreatedBy()).isEqualTo(ADMIN);

            // 세 값이 함께 복원된다 (원본 / 보정 / 최종)
            var breakdown = marketPriceService.breakdown(result.effective());
            assertThat(breakdown.rawPricePerGram()).isEqualByComparingTo("200000.00");
            assertThat(breakdown.adjustment()).isEqualByComparingTo("10000.00");
            assertThat(breakdown.pricePerGram()).isEqualByComparingTo("210000.00");
        }

        @Test
        @DisplayName("수동 고시도 L1 원본을 반드시 통과한다 — effective 는 항상 근거 원본을 가리킨다")
        void manualDeclarationGoesThroughRawLayer() {
            PriceApplyResult result = declare("200000", "관리자 수동 고시");

            assertThat(result.effective().getBaseRawId()).isNotNull();
            MarketPriceRaw raw = rawRepository.findById(result.effective().getBaseRawId()).orElseThrow();
            assertThat(raw.isDeclaredByAdmin()).isTrue();
            assertThat(raw.getCreatedBy()).isEqualTo(ADMIN);
            assertThat(raw.getRawPayload()).contains("MANUAL");
        }

        @Test
        @DisplayName("적용 시점 정책이 스냅샷으로 남아, 나중에 정책을 바꿔도 과거 시세의 근거는 변하지 않는다")
        void policySnapshotIsFrozen() {
            setPolicy(PolicyMode.FIXED_ADJ, new BigDecimal("10000"), null, null);
            PriceApplyResult first = declare("200000", "최초 고시");

            setPolicy(PolicyMode.AUTO, null, null, null);

            MarketPriceEffective reloaded = effectiveRepository.findById(first.effective().getId()).orElseThrow();
            assertThat(reloaded.getPolicyMode()).isEqualTo(PolicyMode.FIXED_ADJ);

            // jsonb 는 PostgreSQL 이 정규화해서 돌려주므로 문자열 비교가 아니라 내용으로 확인한다.
            JsonNode snapshot = readJson(reloaded.getPolicySnapshot());
            assertThat(snapshot.get("mode").asText()).isEqualTo("FIXED_ADJ");
            assertThat(new BigDecimal(snapshot.get("adjAmount").asText())).isEqualByComparingTo("10000.00");
            assertThat(new BigDecimal(snapshot.get("rawPricePerGram").asText()))
                    .isEqualByComparingTo("200000.00");
            assertThat(new BigDecimal(snapshot.get("adjustmentAmount").asText()))
                    .isEqualByComparingTo("10000.00");
            assertThat(new BigDecimal(snapshot.get("resultPricePerGram").asText()))
                    .isEqualByComparingTo("210000.00");
        }
    }

    // =================================================================
    // S2 — 시세 변경 시 이력 보존
    // =================================================================

    @Nested
    @DisplayName("S2 — 시세 변경과 이력 보존")
    class HistoryPreservation {

        @Test
        @DisplayName("시세를 바꾸면 이전 행이 마감되고 신규 행이 발행된다. 이전 행의 값은 그대로다")
        void previousRowIsClosedNotOverwritten() {
            declare("200000", "최초 고시");
            declare("205000", "장중 갱신");   // 2.5% 변동 — 임계값 5% 이내

            List<MarketPriceEffective> rows = effectiveRepository
                    .findByMetalTypeAndPriceSideAndEffectiveFromGreaterThanEqualOrderByEffectiveFromAsc(
                            MetalType.GOLD_24K, PriceSide.SELL, OffsetDateTime.now().minusHours(1));

            assertThat(rows).hasSize(2);

            MarketPriceEffective old = rows.get(0);
            MarketPriceEffective current = rows.get(1);

            // 이전 행: 값은 그대로, effective_to 만 채워졌다
            assertThat(old.getPricePerGram()).isEqualByComparingTo("200000.00");
            assertThat(old.getEffectiveTo()).isNotNull();
            assertThat(old.isCurrent()).isFalse();

            // 현재 행
            assertThat(current.getPricePerGram()).isEqualByComparingTo("205000.00");
            assertThat(current.getEffectiveTo()).isNull();

            // 현재값은 metal×side 당 정확히 1건 (부분 UNIQUE 인덱스가 보장)
            assertThat(effectiveRepository
                    .findByMetalTypeAndPriceSideAndEffectiveToIsNull(MetalType.GOLD_24K, PriceSide.SELL))
                    .isPresent()
                    .get()
                    .extracting(MarketPriceEffective::getId)
                    .isEqualTo(current.getId());
        }

        @Test
        @DisplayName("append-only 트리거 — 발행된 시세의 가격은 UPDATE 로 바꿀 수 없다")
        void triggerBlocksPriceUpdate() {
            PriceApplyResult result = declare("200000", "최초 고시");
            Long id = result.effective().getId();

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE market_prices_effective SET price_per_gram = 999999 WHERE id = ?", id))
                    .hasMessageContaining("only closing effective_to");
        }

        @Test
        @DisplayName("append-only 트리거 — 발행된 시세는 DELETE 할 수 없다")
        void triggerBlocksDelete() {
            PriceApplyResult result = declare("200000", "최초 고시");
            Long id = result.effective().getId();

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM market_prices_effective WHERE id = ?", id))
                    .hasMessageContaining("append-only");
        }

        @Test
        @DisplayName("이력 조회는 최신순으로 전 행을 돌려준다 — 과거 가격이 사라지지 않는다")
        void historyKeepsEveryRow() {
            declare("200000", "1차");
            declare("204000", "2차");
            declare("208000", "3차");

            var page = marketPriceService.history(MetalType.GOLD_24K, PriceSide.SELL,
                    org.springframework.data.domain.PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).extracting(MarketPriceEffective::getPricePerGram)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("208000.00"), new BigDecimal("204000.00"),
                            new BigDecimal("200000.00"));
        }
    }

    // =================================================================
    // S5 — 이상 변동 → pending, 자동 적용 안 됨
    // =================================================================

    @Nested
    @DisplayName("S5 — 이상 변동 승인 대기")
    class AnomalyHold {

        @Test
        @DisplayName("5% 초과 변동은 발행되지 않고 승인 대기로 간다. 현재 시세는 직전 값 그대로다")
        void exceedingChangeIsHeld() {
            declare("200000", "최초 고시");

            // +15% — 임계값 5% 초과
            PriceApplyResult result = declare("230000", "급등 반영 시도");

            assertThat(result.applied()).isFalse();
            assertThat(result.pending()).isNotNull();
            assertThat(result.pending().getStatus()).isEqualTo(PendingStatus.PENDING);
            assertThat(result.changePercent()).isGreaterThan(new BigDecimal("5"));

            // 현재 시세는 바뀌지 않았다
            MarketPriceEffective current = currentEffective();
            assertThat(current.getPricePerGram()).isEqualByComparingTo("200000.00");

            // 발행된 행은 여전히 1건뿐
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM market_prices_effective", Integer.class)).isEqualTo(1);

            // 원본은 저장되고 ANOMALY 로 표시된다 — 값 자체는 보존된다
            MarketPriceRaw raw = rawRepository.findById(result.pending().getRawId()).orElseThrow();
            assertThat(raw.getPricePerGram()).isEqualByComparingTo("230000.00");
            assertThat(raw.getStatus()).isEqualTo(RawStatus.ANOMALY);
        }

        @Test
        @DisplayName("승인하면 그때 발행된다 — 승인 전까지는 적용되지 않는다")
        void approvalAppliesTheHeldPrice() {
            declare("200000", "최초 고시");
            PriceApplyResult held = declare("230000", "급등 반영 시도");
            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("200000.00");

            PriceApplyResult approved = pendingService.approve(
                    held.pending().getId(), "국제 금값 급등 확인", ADMIN);

            assertThat(approved.applied()).isTrue();
            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("230000.00");
            assertThat(currentEffective().getChangeReason()).contains("승인");

            assertThat(pendingRepository.findById(held.pending().getId()).orElseThrow().getStatus())
                    .isEqualTo(PendingStatus.APPROVED);
        }

        @Test
        @DisplayName("반려하면 시세가 변하지 않는다")
        void rejectionChangesNothing() {
            declare("200000", "최초 고시");
            PriceApplyResult held = declare("230000", "급등 반영 시도");

            pendingService.reject(held.pending().getId(), "출처 확인 불가", ADMIN);

            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("200000.00");
            assertThat(pendingRepository.findById(held.pending().getId()).orElseThrow().getStatus())
                    .isEqualTo(PendingStatus.REJECTED);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM market_prices_effective", Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 처리된 승인건을 다시 처리하면 409 — 두 번 클릭에 시세가 두 번 발행되지 않는다")
        void doubleDecisionIsRejected() {
            declare("200000", "최초 고시");
            PriceApplyResult held = declare("230000", "급등 반영 시도");
            pendingService.approve(held.pending().getId(), "1차 승인", ADMIN);

            assertThatThrownBy(() -> pendingService.approve(held.pending().getId(), "중복 승인", ADMIN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("이미 처리됨");
        }

        @Test
        @DisplayName("대기 중 같은 조합으로 또 시도해도 대기건이 중복 생성되지 않는다")
        void doesNotDuplicatePending() {
            declare("200000", "최초 고시");
            PriceApplyResult first = declare("230000", "1차 시도");
            PriceApplyResult second = declare("235000", "2차 시도");

            assertThat(second.applied()).isFalse();
            assertThat(second.pending().getId()).isEqualTo(first.pending().getId());
            assertThat(pendingRepository.countByStatus(PendingStatus.PENDING)).isEqualTo(1);
        }

        @Test
        @DisplayName("최초 고시는 비교 대상이 없으므로 임계값 검사를 타지 않는다")
        void firstIssueIsNeverHeld() {
            PriceApplyResult result = declare("200000", "최초 고시");

            assertThat(result.applied()).isTrue();
            assertThat(result.changePercent()).isNull();
        }

        @Test
        @DisplayName("임계값은 system_settings 로 조정된다 — 재배포 없이 바뀐다")
        void thresholdComesFromSettings() {
            try {
                systemSettingService.update(MarketPriceService.SETTING_MAX_CHANGE_PERCENT, "20", "test");

                declare("200000", "최초 고시");
                PriceApplyResult result = declare("230000", "15% 상승");   // 20% 이내라 통과

                assertThat(result.applied()).isTrue();
                assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("230000.00");
            } finally {
                systemSettingService.update(MarketPriceService.SETTING_MAX_CHANGE_PERCENT, "5", "test");
            }
        }
    }

    // =================================================================
    // S4 — 수집 실패 → 기존 시세 유지
    // =================================================================

    @Nested
    @DisplayName("S4 — 수집 실패 시 기존 시세 유지")
    class CollectionFailure {

        @Test
        @DisplayName("Provider 가 타임아웃으로 실패해도 effective 는 변하지 않고 0원이 되지 않는다")
        void failureKeepsExistingPrice() {
            declare("200000", "최초 고시");
            long effectiveCountBefore = effectiveRepository.count();

            MarketPriceSource krx = source(MarketPriceSource.CODE_KRX);
            collector.collectOne(failingProvider(FetchResult.TIMEOUT, "read timed out"),
                    krx, MetalType.GOLD_24K);

            // 시세 불변
            assertThat(effectiveRepository.count()).isEqualTo(effectiveCountBefore);
            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("200000.00");
            assertThat(marketPriceService.getCurrentPrice(MetalType.GOLD_24K, PriceSide.SELL))
                    .isPresent()
                    .get()
                    .extracting(PricePerGram::value)
                    .isEqualTo(new BigDecimal("200000.00"));

            // 실패는 수집 이력에 남는다 (원본은 만들어지지 않는다)
            assertThat(fetchLogRepository.countByResult(FetchResult.TIMEOUT)).isPositive();
            assertThat(fetchLogRepository.countByResult(FetchResult.SUCCESS)).isZero();
            assertThat(rawRepository.count()).isEqualTo(1);   // 수동 고시 원본 1건뿐
        }

        @Test
        @DisplayName("재시도는 3회까지. 재시도해도 소용없는 실패(PARSE_ERROR)는 1회로 끝낸다")
        void retriesOnlyWhenItCanHelp() {
            collector.collectOne(failingProvider(FetchResult.TIMEOUT, "timeout"),
                    source(MarketPriceSource.CODE_KRX), MetalType.GOLD_24K);
            assertThat(fetchLogRepository.countByResult(FetchResult.TIMEOUT)).isEqualTo(3);

            collector.collectOne(failingProvider(FetchResult.PARSE_ERROR, "unexpected html"),
                    source(MarketPriceSource.CODE_KRX), MetalType.GOLD_24K);
            assertThat(fetchLogRepository.countByResult(FetchResult.PARSE_ERROR)).isEqualTo(1);
        }

        @Test
        @DisplayName("범위를 벗어난 수집값은 원본을 만들지 않고 INVALID_VALUE 로 기록된다")
        void outOfRangeValueIsRejected() {
            declare("200000", "최초 고시");

            MarketPriceSource krx = source(MarketPriceSource.CODE_KRX);
            // 24K 상한(기본 500,000원/g)을 크게 초과 — 자릿수 실수 시나리오
            Long rawId = collectionWriter.store(krx, MetalType.GOLD_24K,
                    new RawPrice(MetalType.GOLD_24K, PricePerGram.of("1370000"), OffsetDateTime.now(), "{}"),
                    10, 1, OffsetDateTime.now());

            assertThat(rawId).isNull();
            assertThat(fetchLogRepository.countByResult(FetchResult.INVALID_VALUE)).isEqualTo(1);
            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("200000.00");
        }

        @Test
        @DisplayName("단위 오입력(돈 가격을 g 칸에) 은 범위 안이라도 걸러진다")
        void unitErrorIsDetected() {
            declare("100000", "최초 고시");

            // 100,000 × 3.75 = 375,000 — 24K 상한 안이지만 '1돈 가격'으로 의심된다
            Long rawId = collectionWriter.store(source(MarketPriceSource.CODE_KRX), MetalType.GOLD_24K,
                    new RawPrice(MetalType.GOLD_24K, PricePerGram.of("375000"), OffsetDateTime.now(), "{}"),
                    10, 1, OffsetDateTime.now());

            assertThat(rawId).isNull();
            assertThat(fetchLogRepository.countByResult(FetchResult.INVALID_VALUE)).isEqualTo(1);
            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("100000.00");
        }

        @Test
        @DisplayName("관리자 수동 고시도 같은 검증을 거친다 — 0 이하는 400 이고 시세는 변하지 않는다")
        void manualDeclarationIsValidatedToo() {
            declare("200000", "최초 고시");

            assertThatThrownBy(() -> declare("0", "0원 입력 시도"))
                    .isInstanceOf(BusinessException.class);

            assertThat(currentEffective().getPricePerGram()).isEqualByComparingTo("200000.00");
        }

        @Test
        @DisplayName("수집 상태 API 가 마지막 성공/시도 시각을 돌려준다")
        void collectionStatusIsReported() {
            var before = collector.status(true);
            assertThat(before.neverSucceeded()).isTrue();
            assertThat(before.lastAttemptAt()).isNull();
            // V14 가 30분 → 10분으로 낮췄다. 발주 요구가 10분 간격이다.
            assertThat(before.cron()).isEqualTo("0 0/10 10-18 * * MON-FRI");

            MarketPriceSource krx = source(MarketPriceSource.CODE_KRX);
            collector.collectOne(failingProvider(FetchResult.HTTP_ERROR, "503"), krx, MetalType.GOLD_24K);

            var afterFailure = collector.status(true);
            assertThat(afterFailure.lastAttemptAt()).isNotNull();
            assertThat(afterFailure.lastAttemptResult()).isEqualTo(FetchResult.HTTP_ERROR);
            assertThat(afterFailure.neverSucceeded()).isTrue();
            assertThat(afterFailure.failureCount()).isEqualTo(3);

            collectionWriter.store(krx, MetalType.GOLD_24K,
                    new RawPrice(MetalType.GOLD_24K, PricePerGram.of("200000"), OffsetDateTime.now(), "{}"),
                    10, 1, OffsetDateTime.now());

            var afterSuccess = collector.status(true);
            assertThat(afterSuccess.neverSucceeded()).isFalse();
            assertThat(afterSuccess.lastSuccessAt()).isNotNull();
        }

        @Test
        @DisplayName("KRX 공급자는 기본 비활성이라 컨텍스트에 올라오지 않는다 (TODO B-1)")
        void krxProviderIsDisabledByDefault() {
            // 폴링 대상 공급자가 없으므로 수집기가 아무 일도 하지 않는다
            collector.collectAll();
            assertThat(fetchLogRepository.count()).isZero();
        }
    }

    // =================================================================
    // S6 — 시세 없음 / 노후 → "시세문의"
    // =================================================================

    @Nested
    @DisplayName("S6 — 시세문의 폴백")
    class UnavailableFallback {

        @Test
        @DisplayName("한 번도 고시된 적 없으면 empty 다 — 0 이나 예외가 아니다")
        void noPriceReturnsEmpty() {
            Optional<PricePerGram> price =
                    marketPriceService.getCurrentPrice(MetalType.GOLD_24K, PriceSide.SELL);

            assertThat(price).isEmpty();
            assertThat(marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL).availability())
                    .isEqualTo(CurrentPriceView.Availability.NONE);
        }

        @Test
        @DisplayName("PRICE_STALE_MINUTES 를 넘긴 시세는 값이 있어도 empty 다")
        void stalePriceReturnsEmpty() {
            declare("200000", "최초 고시");
            assertThat(marketPriceService.getCurrentPrice(MetalType.GOLD_24K, PriceSide.SELL)).isPresent();

            // 노후 판정 기준을 0분으로 낮춰 즉시 노후 상태를 만든다
            try {
                systemSettingService.update(MarketPriceService.SETTING_STALE_MINUTES, "0", "test");
                evictPriceCache();

                assertThat(marketPriceService.getCurrentPrice(MetalType.GOLD_24K, PriceSide.SELL)).isEmpty();

                CurrentPriceView view =
                        marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL);
                assertThat(view.availability()).isEqualTo(CurrentPriceView.Availability.STALE);
                // 관리자 화면은 노후된 값도 봐야 하므로 행 자체는 남아 있다
                assertThat(view.effective().getPricePerGram()).isEqualByComparingTo("200000.00");
                assertThat(view.isSellable()).isFalse();
            } finally {
                systemSettingService.update(MarketPriceService.SETTING_STALE_MINUTES, "240", "test");
                evictPriceCache();
            }
        }

        @Test
        @DisplayName("전 금속×side 현황은 고시된 적 없는 조합도 NONE 으로 빠짐없이 채운다")
        void allCombinationsAreReported() {
            declare("200000", "최초 고시");

            List<CurrentPriceView> views = marketPriceService.describeAllCurrent();

            assertThat(views).hasSize(MetalType.values().length * PriceSide.values().length);
            assertThat(views).filteredOn(CurrentPriceView::isSellable).hasSize(1);
            assertThat(views).filteredOn(v -> v.availability() == CurrentPriceView.Availability.NONE)
                    .allSatisfy(v -> assertThat(v.effective()).isNull());
        }
    }

    @Nested
    @DisplayName("변동폭 — 직전 고시 대비")
    class PriceChangeCalculation {

        @Test
        @DisplayName("최초 고시는 비교 대상이 없어 변동폭이 null 이다 — 0% 로 채우지 않는다")
        void firstDeclarationHasNoChange() {
            declare("200000", "최초 고시");

            CurrentPriceView view = marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL);
            PublicPriceResponse response = PublicPriceResponse.of(
                    view, marketPriceService.findPreviousByKey()
                            .get(MarketPriceService.previousKey(MetalType.GOLD_24K, PriceSide.SELL)));

            assertThat(response.available()).isTrue();
            assertThat(response.change()).isNull();
        }

        @Test
        @DisplayName("두 번째 고시부터 직전 행 대비 변동액·변동률이 계산된다")
        void secondDeclarationReportsChange() {
            declare("200000", "최초 고시");
            declare("194000", "장중 하락");   // -3.00%

            MarketPriceEffective previous = marketPriceService.findPreviousByKey()
                    .get(MarketPriceService.previousKey(MetalType.GOLD_24K, PriceSide.SELL));
            assertThat(previous).isNotNull();
            assertThat(previous.getPricePerGram()).isEqualByComparingTo("200000.00");

            PublicPriceResponse response = PublicPriceResponse.of(
                    marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL), previous);

            assertThat(response.change()).isNotNull();
            assertThat(response.change().previousPricePerGram()).isEqualByComparingTo("200000.00");
            assertThat(response.change().changePerGram()).isEqualByComparingTo("-6000.00");
            // 시세표는 1돈(3.75g) 기준으로 표시한다
            assertThat(response.change().changePerDon()).isEqualByComparingTo("-22500.00");
            assertThat(response.change().changePercent()).isEqualByComparingTo("-3.00");
        }

        @Test
        @DisplayName("직전 시세는 마감된 행 중 가장 최근이다 — 세 번 고시하면 두 번째와 비교한다")
        void previousIsTheMostRecentlyClosedRow() {
            declare("200000", "1차");
            declare("204000", "2차");
            declare("208000", "3차");

            MarketPriceEffective previous = marketPriceService.findPreviousByKey()
                    .get(MarketPriceService.previousKey(MetalType.GOLD_24K, PriceSide.SELL));

            assertThat(previous.getPricePerGram()).isEqualByComparingTo("204000.00");
        }

        @Test
        @DisplayName("사용할 수 없는 시세에는 변동폭을 담지 않는다 — 가격이 '시세문의'인데 변동률만 뜨면 안 된다")
        void unavailablePriceCarriesNoChange() {
            declare("200000", "최초 고시");
            declare("194000", "장중 하락");

            MarketPriceEffective previous = marketPriceService.findPreviousByKey()
                    .get(MarketPriceService.previousKey(MetalType.GOLD_24K, PriceSide.SELL));

            try {
                systemSettingService.update(MarketPriceService.SETTING_STALE_MINUTES, "0", "test");
                evictPriceCache();

                PublicPriceResponse response = PublicPriceResponse.of(
                        marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL), previous);

                assertThat(response.available()).isFalse();
                assertThat(response.displayText()).isEqualTo(PublicPriceResponse.UNAVAILABLE_TEXT);
                assertThat(response.change()).isNull();
            } finally {
                systemSettingService.update(MarketPriceService.SETTING_STALE_MINUTES, "240", "test");
                evictPriceCache();
            }
        }

        @Test
        @DisplayName("고시된 적 없는 조합은 직전 시세 맵에 아예 담기지 않는다")
        void neverDeclaredCombinationIsAbsent() {
            declare("200000", "최초 고시");

            assertThat(marketPriceService.findPreviousByKey())
                    .doesNotContainKey(MarketPriceService.previousKey(MetalType.SILVER, PriceSide.BUY));
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    // =================================================================
    // 살 때·팔 때 동시 고시
    // =================================================================

    @Nested
    @DisplayName("한쪽만 고시되는 상태를 만들지 않는다")
    class PairedDeclaration {

        @Test
        @DisplayName("살 때와 팔 때가 한 번에 발행된다 — 둘 중 하나만 있는 시세표가 생기지 않는다")
        void bothSidesPublishTogether() {
            setPolicy(PolicyMode.AUTO, null, null, null);
            setPolicyFor(PriceSide.BUY, PolicyMode.AUTO);

            var result = declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("220800"), new BigDecimal("188000"), "오전 고시", ADMIN);

            assertThat(result.applied()).isTrue();
            assertThat(current(PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("220800.00");
            assertThat(current(PriceSide.BUY).getPricePerGram()).isEqualByComparingTo("188000.00");
        }

        @Test
        @DisplayName("살 때가 팔 때보다 크지 않으면 거절한다 — 사고 되팔기만 해도 이익이 나는 상태다")
        void invertedSpreadIsRejected() {
            assertThatThrownBy(() -> declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("188000"), new BigDecimal("220800"), "실수", ADMIN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("살 때");

            assertThat(effectiveRepository.findAll())
                    .as("거절됐으면 어느 쪽도 발행되지 않는다")
                    .isEmpty();
        }

        @Test
        @DisplayName("두 값이 같아도 거절한다 — 스프레드가 0 이면 거래할수록 비용만 나간다")
        void zeroSpreadIsRejected() {
            assertThatThrownBy(() -> declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("200000"), new BigDecimal("200000"), "실수", ADMIN))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("한쪽이 변동률 임계를 넘으면 둘 다 발행하지 않는다")
        void oneSideOverLimitHoldsBoth() {
            setPolicy(PolicyMode.AUTO, null, null, null);
            setPolicyFor(PriceSide.BUY, PolicyMode.AUTO);

            // 기준을 만든다.
            declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("220800"), new BigDecimal("188000"), "기준", ADMIN);

            // 살 때만 두 배로 튀게 한다. 팔 때는 정상 범위다.
            assertThatThrownBy(() -> declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("441600"), new BigDecimal("188100"), "오타", ADMIN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("한쪽만 고시되면");

            // 예외로 트랜잭션이 되돌아가 두 값 모두 직전 상태 그대로다.
            assertThat(current(PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("220800.00");
            assertThat(current(PriceSide.BUY).getPricePerGram()).isEqualByComparingTo("188000.00");
        }
    }

    private MarketPriceEffective current(PriceSide side) {
        return effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(MetalType.GOLD_24K, side)
                .orElseThrow(() -> new AssertionError("현재 시세가 없습니다: " + side));
    }

    private void setPolicyFor(PriceSide side, PolicyMode mode) {
        policyService.update(MetalType.GOLD_24K, side, mode,
                BigDecimal.ZERO, BigDecimal.ZERO, null, "테스트 설정", ADMIN);
    }

    private PriceApplyResult declare(String pricePerGram, String reason) {
        return declarationService.declare(MetalType.GOLD_24K, PriceSide.SELL,
                new BigDecimal(pricePerGram), reason, ADMIN);
    }

    private void setPolicy(PolicyMode mode, BigDecimal adjAmount, BigDecimal adjPercent, BigDecimal manualPrice) {
        policyService.update(MetalType.GOLD_24K, PriceSide.SELL, mode,
                adjAmount, adjPercent, manualPrice, "테스트 정책 설정", ADMIN);
        MarketPricePolicy reloaded = policyRepository
                .findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.SELL).orElseThrow();
        assertThat(reloaded.getMode()).isEqualTo(mode);
    }

    private MarketPriceEffective currentEffective() {
        return effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(MetalType.GOLD_24K, PriceSide.SELL)
                .orElseThrow(() -> new AssertionError("현재 시세가 있어야 합니다"));
    }

    private MarketPriceSource source(String code) {
        return sourceRepository.findByCode(code).orElseThrow();
    }

    /** 항상 지정한 종류로 실패하는 Provider. 외부 API 장애를 재현한다. */
    private MarketPriceProvider failingProvider(FetchResult result, String message) {
        return new MarketPriceProvider() {
            @Override
            public String sourceCode() {
                return MarketPriceSource.CODE_KRX;
            }

            @Override
            public Optional<RawPrice> fetch(MetalType metal) {
                throw new MarketPriceFetchException(result, message);
            }

            @Override
            public boolean pollable() {
                return true;
            }

            @Override
            public List<MetalType> supportedMetals() {
                return List.of(MetalType.GOLD_24K);
            }
        };
    }

    /** getCurrentPrice 는 캐시된다. 설정을 바꿔 즉시 확인하려면 비워야 한다. */
    private void evictPriceCache() {
        var cache = cacheManager.getCache(com.hanseongge.global.config.CacheConfig.MARKET_PRICES);
        if (cache != null) {
            cache.clear();
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("JSON 파싱 실패: " + json, e);
        }
    }

    @Autowired org.springframework.cache.CacheManager cacheManager;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
}
