package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.marketprice.dto.PublicPriceResponse;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.entity.RawStatus;
import com.hanseongge.domain.marketprice.repository.MarketPriceEffectiveRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import com.hanseongge.domain.marketprice.service.DerivationUpdate;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.marketprice.service.MarketPricePolicyService;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
import com.hanseongge.domain.marketprice.service.QuoteLine;
import com.hanseongge.domain.marketprice.service.QuotePlan;
import com.hanseongge.domain.marketprice.service.QuotePublished;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시세 고시 검증 — 관리자가 고시한 값만 쓴다 (V19).
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>입력한 값이 곧 고시가다. 누가 무엇을 입력했는지는 원본으로 남는다</li>
 *   <li>고시는 덮어쓰지 않는다 — 이전 행을 마감하고 새 행을 쌓는다</li>
 *   <li>직전보다 크게 바뀌면 확인 없이는 아무것도 저장되지 않는다</li>
 *   <li>검증에 걸리면 아무것도 저장되지 않는다. 지금 값이 그대로다</li>
 *   <li>한 번 고시한 값은 시간이 지나도 유효하다</li>
 *   <li>24K 를 고시하면 환산으로 설정된 18K·14K 가 같은 트랜잭션·같은 시각으로 나간다</li>
 * </ul>
 *
 * <p>실제 PostgreSQL 위에서 돈다. append-only 트리거·부분 UNIQUE 인덱스·advisory lock·
 * V19 의 환산 제약이 <b>실제로 동작하는지</b>가 핵심이라 H2 로는 대체할 수 없다.
 */
@SpringBootTest
class MarketPriceIntegrationTest extends EmbeddedPostgresSupport {

    private static final String ADMIN = "test_price_admin";

    @Autowired MarketPriceService marketPriceService;
    @Autowired MarketPriceDeclarationService declarationService;
    @Autowired MarketPricePolicyService policyService;

    @Autowired MarketPriceEffectiveRepository effectiveRepository;
    @Autowired MarketPriceRawRepository rawRepository;
    @Autowired MarketPricePolicyRepository policyRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CacheManager cacheManager;
    @Autowired ObjectMapper objectMapper;

    /**
     * 시세 테이블을 비우고 설정을 시드 상태로 되돌린다.
     *
     * <p>{@code market_prices_effective} 는 DELETE 트리거가 막으므로 잠시 끈다.
     * <b>테스트에서만</b> 하는 일이다 — 애플리케이션에는 이 테이블을 지우는 경로가 없다.
     */
    @BeforeEach
    @AfterEach
    void resetMarketPriceTables() {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        try {
            jdbcTemplate.update("DELETE FROM market_prices_effective");
            jdbcTemplate.update("DELETE FROM market_price_raw");
            jdbcTemplate.update("UPDATE market_price_policies "
                    + "SET mode='DIRECT', derive_from=NULL, derive_ratio=NULL, updated_by=NULL");
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
        }
        jdbcTemplate.update("UPDATE system_settings SET value='5' WHERE key='MAX_PRICE_CHANGE_PERCENT'");
        evictCaches();
    }

    // =================================================================
    // 입력한 값이 곧 고시가
    // =================================================================

    @Nested
    @DisplayName("입력한 값이 곧 고시가다")
    class DeclaredAsIs {

        @Test
        @DisplayName("살 때·팔 때가 입력한 값 그대로, 같은 발행 시각으로 나간다")
        void publishedAsTyped() {
            QuotePublished published = declare("220800", "188000");

            MarketPriceEffective sell = current(MetalType.GOLD_24K, PriceSide.SELL);
            MarketPriceEffective buy = current(MetalType.GOLD_24K, PriceSide.BUY);

            assertThat(sell.getPricePerGram()).isEqualByComparingTo("220800");
            assertThat(buy.getPricePerGram()).isEqualByComparingTo("188000");
            assertThat(sell.getPolicyMode()).isEqualTo(PolicyMode.DIRECT);
            assertThat(published.issued()).hasSize(2);
            assertThat(sell.getEffectiveFrom())
                    .as("한 번의 고시가 이력에서 두 줄로 갈라지지 않는다")
                    .isEqualTo(buy.getEffectiveFrom());
        }

        @Test
        @DisplayName("누가 무엇을 입력했는지 살 때·팔 때 각각 원본으로 남는다")
        void rawRecordsWhatWasTyped() {
            declare("220800", "188000");

            MarketPriceEffective sell = current(MetalType.GOLD_24K, PriceSide.SELL);
            MarketPriceEffective buy = current(MetalType.GOLD_24K, PriceSide.BUY);
            MarketPriceRaw sellRaw = rawRepository.findById(sell.getBaseRawId()).orElseThrow();
            MarketPriceRaw buyRaw = rawRepository.findById(buy.getBaseRawId()).orElseThrow();

            assertThat(sellRaw.getId()).isNotEqualTo(buyRaw.getId());
            assertThat(sellRaw.getPriceSide()).isEqualTo(PriceSide.SELL);
            assertThat(buyRaw.getPriceSide()).isEqualTo(PriceSide.BUY);
            assertThat(sellRaw.getPricePerGram()).isEqualByComparingTo("220800");
            assertThat(sellRaw.isDeclaredByAdmin()).isTrue();
            assertThat(sellRaw.getCreatedBy()).isEqualTo(ADMIN);
            assertThat(sellRaw.getStatus()).isEqualTo(RawStatus.NORMAL);
            assertThat(sellRaw.getRawPayload()).contains("MANUAL");
        }

        @Test
        @DisplayName("발행 근거가 스냅샷으로 남는다")
        void snapshotRecordsHowItWasMade() {
            declare("220800", "188000");

            JsonNode snapshot = readJson(current(MetalType.GOLD_24K, PriceSide.SELL).getPolicySnapshot());
            assertThat(snapshot.get("mode").asText()).isEqualTo("DIRECT");
            assertThat(new BigDecimal(snapshot.get("pricePerGram").asText())).isEqualByComparingTo("220800");
        }

        @Test
        @DisplayName("18K 가 직접 입력이면 24K 를 고시해도 18K 는 움직이지 않는다")
        void directMetalsStayPut() {
            declare("220800", "188000");

            assertThat(marketPriceService.findCurrent(MetalType.GOLD_18K, PriceSide.SELL)).isEmpty();
        }
    }

    // =================================================================
    // 이력 보존
    // =================================================================

    @Nested
    @DisplayName("시세 변경과 이력 보존")
    class HistoryPreservation {

        @Test
        @DisplayName("시세를 바꾸면 이전 행이 마감되고 신규 행이 발행된다. 이전 행의 값은 그대로다")
        void previousRowIsClosedNotOverwritten() {
            declare("200000", "180000");
            declare("205000", "184500");   // 2.5% — 확인 기준 5% 이내

            List<MarketPriceEffective> rows = effectiveRepository
                    .findByMetalTypeAndPriceSideAndEffectiveFromGreaterThanEqualOrderByEffectiveFromAsc(
                            MetalType.GOLD_24K, PriceSide.SELL, OffsetDateTime.now().minusHours(1));

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getPricePerGram()).isEqualByComparingTo("200000");
            assertThat(rows.get(0).getEffectiveTo()).isNotNull();
            assertThat(rows.get(1).getPricePerGram()).isEqualByComparingTo("205000");
            assertThat(rows.get(1).getEffectiveTo()).isNull();
        }

        @Test
        @DisplayName("append-only 트리거 — 발행된 시세의 가격은 UPDATE 로 바꿀 수 없다")
        void triggerBlocksPriceUpdate() {
            declare("200000", "180000");
            Long id = current(MetalType.GOLD_24K, PriceSide.SELL).getId();

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE market_prices_effective SET price_per_gram = 999999 WHERE id = ?", id))
                    .hasMessageContaining("only closing effective_to");
        }

        @Test
        @DisplayName("append-only 트리거 — 발행된 시세는 DELETE 할 수 없다")
        void triggerBlocksDelete() {
            declare("200000", "180000");
            Long id = current(MetalType.GOLD_24K, PriceSide.SELL).getId();

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM market_prices_effective WHERE id = ?", id))
                    .hasMessageContaining("append-only");
        }

        @Test
        @DisplayName("이력 조회는 최신순으로 전 행을 돌려준다 — 과거 가격이 사라지지 않는다")
        void historyKeepsEveryRow() {
            declare("200000", "180000");
            declare("204000", "183600");
            declare("208000", "187200");

            var page = marketPriceService.history(MetalType.GOLD_24K, PriceSide.SELL,
                    org.springframework.data.domain.PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(MarketPriceEffective::getPricePerGram)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("208000"), new BigDecimal("204000"),
                            new BigDecimal("200000"));
        }

        @Test
        @DisplayName("기간 이력은 그 기간이 시작할 때 적용 중이던 값부터 준다 — 하루 한 번 고시해도 1일 그래프가 이어진다")
        void periodHistoryStartsWithValueInEffect() {
            declare("200000", "180000");
            backdateCurrent("3 days");
            declare("204000", "183600");

            List<MarketPriceEffective> day =
                    marketPriceService.historySince(MetalType.GOLD_24K, PriceSide.SELL, 1);

            assertThat(day).extracting(MarketPriceEffective::getPricePerGram)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("200000"), new BigDecimal("204000"));
            assertThat(day.get(0).getEffectiveFrom())
                    .as("맨 앞 행의 발행 시각은 고치지 않는다 — 기간 밖에서 고시된 사실 그대로다")
                    .isBefore(OffsetDateTime.now().minusDays(1));
        }

        @Test
        @DisplayName("기간 안에 모두 들어오면 앞에 덧붙이는 값이 없다 — 같은 고시가 두 번 나오지 않는다")
        void periodHistoryHasNoDuplicateWhenAllInside() {
            declare("200000", "180000");
            declare("204000", "183600");

            assertThat(marketPriceService.historySince(MetalType.GOLD_24K, PriceSide.SELL, 7)).hasSize(2);
        }

        @Test
        @DisplayName("고시된 적 없으면 기간 이력도 비어 있다 — 없는 값을 만들어 붙이지 않는다")
        void periodHistoryIsEmptyWhenNeverQuoted() {
            assertThat(marketPriceService.historySince(MetalType.GOLD_24K, PriceSide.SELL, 1)).isEmpty();
        }
    }

    // =================================================================
    // 크게 바뀌면 확인
    // =================================================================

    @Nested
    @DisplayName("직전보다 크게 바뀌면 고시 전에 확인받는다")
    class LargeChangeConfirmation {

        @Test
        @DisplayName("미리보기는 무엇이 얼마나 바뀌는지만 알려주고 아무것도 쓰지 않는다")
        void previewWritesNothing() {
            declare("200000", "180000");

            QuotePlan plan = declarationService.preview(MetalType.GOLD_24K,
                    new BigDecimal("230000"), new BigDecimal("207000"));   // +15%

            assertThat(plan.needsConfirm()).isTrue();
            QuoteLine sell = line(plan, MetalType.GOLD_24K, PriceSide.SELL);
            assertThat(sell.currentPricePerGram()).isEqualByComparingTo("200000");
            assertThat(sell.newPrice().value()).isEqualByComparingTo("230000");
            assertThat(sell.changePercent()).isEqualByComparingTo("15.00");
            assertThat(sell.exceedsLimit()).isTrue();

            assertThat(count("market_prices_effective")).isEqualTo(2);
            assertThat(count("market_price_raw")).isEqualTo(2);
        }

        @Test
        @DisplayName("확인 없이 기준을 넘는 고시는 거절되고 원본까지 아무것도 남지 않는다")
        void unconfirmedLargeChangeIsRejected() {
            declare("200000", "180000");

            assertThatThrownBy(() -> declare("230000", "207000"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_CHANGE_NEEDS_CONFIRM);
                        assertThat(e.getFieldErrors()).containsKeys("GOLD_24K/SELL", "GOLD_24K/BUY");
                    });

            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("200000");
            assertThat(count("market_price_raw"))
                    .as("거절된 입력은 '누가 무엇을 고시했나' 기록에 섞이지 않는다")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("확인하면 그대로 고시된다")
        void confirmedLargeChangeIsPublished() {
            declare("200000", "180000");

            declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("230000"), new BigDecimal("207000"), "급등 확인", ADMIN, true);

            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("230000");
        }

        @Test
        @DisplayName("처음 고시는 비교 대상이 없어 확인을 묻지 않는다")
        void firstDeclarationNeverNeedsConfirm() {
            QuotePlan plan = declarationService.preview(MetalType.GOLD_24K,
                    new BigDecimal("220800"), new BigDecimal("188000"));

            assertThat(plan.needsConfirm()).isFalse();
            assertThat(plan.lines()).allSatisfy(l -> {
                assertThat(l.currentPricePerGram()).isNull();
                assertThat(l.changePercent()).isNull();
            });
        }

        @Test
        @DisplayName("한쪽만 기준을 넘어도 확인 대상이고, 확인 없이는 두 쪽 다 그대로다")
        void oneSideOverLimitNeedsConfirm() {
            declare("220800", "188000");

            QuotePlan plan = declarationService.preview(MetalType.GOLD_24K,
                    new BigDecimal("221000"), new BigDecimal("220000"));

            assertThat(plan.needsConfirm()).isTrue();
            assertThat(line(plan, MetalType.GOLD_24K, PriceSide.SELL).exceedsLimit()).isFalse();
            assertThat(line(plan, MetalType.GOLD_24K, PriceSide.BUY).exceedsLimit()).isTrue();

            assertThatThrownBy(() -> declare("221000", "220000")).isInstanceOf(BusinessException.class);
            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("220800");
            assertThat(current(MetalType.GOLD_24K, PriceSide.BUY).getPricePerGram())
                    .isEqualByComparingTo("188000");
        }

        @Test
        @DisplayName("작은 변동은 확인 없이 바로 고시된다")
        void smallChangeNeedsNoConfirm() {
            declare("200000", "180000");
            declare("204000", "183600");   // +2%

            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("204000");
        }

        @Test
        @DisplayName("확인 기준은 system_settings 로 조정된다 — 재배포 없이 바뀐다")
        void limitComesFromSettings() {
            jdbcTemplate.update("UPDATE system_settings SET value='20' WHERE key='MAX_PRICE_CHANGE_PERCENT'");
            evictCaches();

            declare("200000", "180000");
            declare("230000", "207000");   // +15% — 20% 이내

            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("230000");
        }
    }

    // =================================================================
    // 값 검증
    // =================================================================

    @Nested
    @DisplayName("값 검증 — 걸리면 아무것도 쓰지 않는다")
    class Validation {

        @Test
        @DisplayName("자릿수 실수(하한 미만)는 어느 쪽인지 짚어서 거절한다")
        void belowMinimumIsRejected() {
            assertThatThrownBy(() -> declare("12000", "11000"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_INVALID_VALUE);
                        assertThat(e.getFieldErrors()).containsKey("GOLD_24K/SELL");
                        assertThat(e.getFieldErrors().get("GOLD_24K/SELL")).contains("하한");
                    });

            assertThat(count("market_prices_effective")).isZero();
            assertThat(count("market_price_raw")).isZero();
        }

        @Test
        @DisplayName("1돈 가격을 1g 칸에 넣은 값(3.75배)은 범위 안이어도 거절한다")
        void unitErrorIsRejected() {
            declare("100000", "90000");

            assertThatThrownBy(() -> declarationService.declareBoth(MetalType.GOLD_24K,
                    new BigDecimal("375000"), new BigDecimal("337500"), "단위 실수", ADMIN, true))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getFieldErrors().get("GOLD_24K/SELL")).contains("1돈"));

            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("0 은 거절한다 — 가격이 0 이 되는 경로는 없다")
        void zeroIsRejected() {
            declare("200000", "180000");

            assertThatThrownBy(() -> declare("0", "0"))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_INVALID_VALUE));

            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("살 때가 팔 때보다 크지 않으면 거절한다 — 사고 되팔기만 해도 이익이 나는 상태다")
        void invertedSpreadIsRejected() {
            assertThatThrownBy(() -> declare("188000", "220800"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_SPREAD_INVERTED);
                        assertThat(e.getMessage()).contains("살 때");
                    });

            assertThat(effectiveRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("두 값이 같아도 거절한다 — 스프레드가 0 이면 거래할수록 비용만 나간다")
        void zeroSpreadIsRejected() {
            assertThatThrownBy(() -> declare("200000", "200000"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // =================================================================
    // 시세문의 폴백
    // =================================================================

    @Nested
    @DisplayName("시세문의 폴백")
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
        @DisplayName("한 번 고시한 값은 며칠이 지나도 그대로 판다 — 장이 닫힌 동안에도 시간은 흐른다")
        void lastQuoteStaysValid() {
            declare("200000", "180000");
            backdateCurrent("3 days");

            CurrentPriceView view = marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL);

            assertThat(view.isSellable()).isTrue();
            assertThat(marketPriceService.getCurrentPrice(MetalType.GOLD_24K, PriceSide.SELL)).isPresent();
        }

        @Test
        @DisplayName("전 금속×side 현황은 고시된 적 없는 조합도 NONE 으로 빠짐없이 채운다")
        void allCombinationsAreReported() {
            declare("200000", "180000");

            List<CurrentPriceView> views = marketPriceService.describeAllCurrent();

            assertThat(views).hasSize(MetalType.values().length * PriceSide.values().length);
            assertThat(views).filteredOn(CurrentPriceView::isSellable).hasSize(2);
            assertThat(views).filteredOn(v -> v.availability() == CurrentPriceView.Availability.NONE)
                    .allSatisfy(v -> assertThat(v.effective()).isNull());
        }
    }

    // =================================================================
    // 변동폭
    // =================================================================

    @Nested
    @DisplayName("변동폭 — 직전 고시 대비")
    class PriceChangeCalculation {

        @Test
        @DisplayName("처음 고시는 비교 대상이 없어 변동폭이 null 이다 — 0% 로 채우지 않는다")
        void firstDeclarationHasNoChange() {
            declare("200000", "180000");

            PublicPriceResponse response = PublicPriceResponse.of(
                    marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL),
                    previous(MetalType.GOLD_24K, PriceSide.SELL));

            assertThat(response.available()).isTrue();
            assertThat(response.change()).isNull();
        }

        @Test
        @DisplayName("두 번째 고시부터 직전 행 대비 변동액·변동률이 계산된다")
        void secondDeclarationReportsChange() {
            declare("200000", "180000");
            declare("194000", "174600");   // -3.00%

            PublicPriceResponse response = PublicPriceResponse.of(
                    marketPriceService.describeCurrent(MetalType.GOLD_24K, PriceSide.SELL),
                    previous(MetalType.GOLD_24K, PriceSide.SELL));

            assertThat(response.change().previousPricePerGram()).isEqualByComparingTo("200000");
            assertThat(response.change().changePerGram()).isEqualByComparingTo("-6000");
            assertThat(response.change().changePerDon()).isEqualByComparingTo("-22500");
            assertThat(response.change().changePercent()).isEqualByComparingTo("-3.00");
        }

        @Test
        @DisplayName("직전 시세는 마감된 행 중 가장 최근이다 — 세 번 고시하면 두 번째와 비교한다")
        void previousIsTheMostRecentlyClosedRow() {
            declare("200000", "180000");
            declare("204000", "183600");
            declare("208000", "187200");

            assertThat(previous(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("204000");
        }

        @Test
        @DisplayName("고시된 적 없는 조합은 직전 시세 맵에 아예 담기지 않는다")
        void neverDeclaredCombinationIsAbsent() {
            declare("200000", "180000");

            assertThat(marketPriceService.findPreviousByKey())
                    .doesNotContainKey(MarketPriceService.previousKey(MetalType.SILVER, PriceSide.BUY));
        }
    }

    // =================================================================
    // 18K·14K 24K 환산
    // =================================================================

    @Nested
    @DisplayName("18K·14K 는 24K 에서 환산할 수 있다")
    class Derivation {

        @Test
        @DisplayName("24K 가 없을 때 환산을 켜면 설정만 저장되고 아무것도 발행되지 않는다")
        void settingWithoutBaseSavesOnly() {
            DerivationUpdate update = derive(MetalType.GOLD_18K, "0.735");

            assertThat(update.published()).isNull();
            for (PriceSide side : PriceSide.values()) {
                MarketPricePolicy policy = policy(MetalType.GOLD_18K, side);
                assertThat(policy.getMode()).isEqualTo(PolicyMode.DERIVED);
                assertThat(policy.getDeriveFrom()).isEqualTo(MetalType.GOLD_24K);
                assertThat(policy.getDeriveRatio()).isEqualByComparingTo("0.7350");
            }
            assertThat(marketPriceService.findCurrent(MetalType.GOLD_18K, PriceSide.SELL)).isEmpty();
        }

        @Test
        @DisplayName("24K 를 고시하면 18K 가 같은 시각으로 함께 나간다 — 살 때 올림, 팔 때 내림")
        void declaring24KPublishesDerived() {
            derive(MetalType.GOLD_18K, "0.735");

            QuotePublished published = declare("220801", "188001");

            MarketPriceEffective base = current(MetalType.GOLD_24K, PriceSide.SELL);
            MarketPriceEffective sell18 = current(MetalType.GOLD_18K, PriceSide.SELL);
            MarketPriceEffective buy18 = current(MetalType.GOLD_18K, PriceSide.BUY);

            assertThat(published.issued()).hasSize(4);
            // 220,801 × 0.735 = 162,288.735 → 올림
            assertThat(sell18.getPricePerGram()).isEqualByComparingTo("162289");
            // 188,001 × 0.735 = 138,180.735 → 내림
            assertThat(buy18.getPricePerGram()).isEqualByComparingTo("138180");
            assertThat(sell18.getPolicyMode()).isEqualTo(PolicyMode.DERIVED);
            assertThat(sell18.getEffectiveFrom()).isEqualTo(base.getEffectiveFrom());
            assertThat(sell18.getBaseRawId())
                    .as("환산값의 근거는 사람이 입력한 24K 원본이다")
                    .isEqualTo(base.getBaseRawId());

            JsonNode snapshot = readJson(sell18.getPolicySnapshot());
            assertThat(new BigDecimal(snapshot.get("deriveRatio").asText())).isEqualByComparingTo("0.735");
            assertThat(new BigDecimal(snapshot.get("basePricePerGram").asText())).isEqualByComparingTo("220801");
        }

        @Test
        @DisplayName("미리보기에도 환산 금속이 함께 나온다")
        void previewIncludesDerivedLines() {
            derive(MetalType.GOLD_18K, "0.735");
            derive(MetalType.GOLD_14K, "0.57");

            QuotePlan plan = declarationService.preview(MetalType.GOLD_24K,
                    new BigDecimal("220800"), new BigDecimal("188000"));

            assertThat(plan.lines()).hasSize(6);
            // 220,800 × 0.57 = 125,856
            assertThat(line(plan, MetalType.GOLD_14K, PriceSide.SELL).newPrice().value())
                    .isEqualByComparingTo("125856");
            assertThat(count("market_prices_effective")).isZero();
        }

        @Test
        @DisplayName("비율을 바꾸면 지금 24K 기준으로 바로 다시 나간다")
        void changingRatioRepublishesImmediately() {
            derive(MetalType.GOLD_18K, "0.735");
            declare("220800", "188000");   // 18K 162,288 / 138,180

            DerivationUpdate update = policyService.updateDerivation(MetalType.GOLD_18K,
                    PolicyMode.DERIVED, new BigDecimal("0.75"), "비율 조정", ADMIN, false);   // +2.04%

            assertThat(update.published()).isNotNull();
            assertThat(current(MetalType.GOLD_18K, PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("165600");
            assertThat(current(MetalType.GOLD_18K, PriceSide.BUY).getPricePerGram()).isEqualByComparingTo("141000");
            assertThat(current(MetalType.GOLD_24K, PriceSide.SELL).getPricePerGram())
                    .as("24K 는 건드리지 않는다")
                    .isEqualByComparingTo("220800");
        }

        @Test
        @DisplayName("비율 변경이 크게 바꾸면 확인 없이는 설정도 시세도 그대로다")
        void largeRatioChangeNeedsConfirm() {
            derive(MetalType.GOLD_18K, "0.735");
            declare("220800", "188000");

            assertThatThrownBy(() -> policyService.updateDerivation(MetalType.GOLD_18K,
                    PolicyMode.DERIVED, new BigDecimal("0.57"), "오타", ADMIN, false))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_CHANGE_NEEDS_CONFIRM));

            assertThat(policy(MetalType.GOLD_18K, PriceSide.SELL).getDeriveRatio()).isEqualByComparingTo("0.735");
            assertThat(current(MetalType.GOLD_18K, PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("162288");
        }

        @Test
        @DisplayName("같은 비율로 다시 저장하면 같은 값의 행을 또 쌓지 않는다")
        void sameRatioDoesNotRepublish() {
            declare("220800", "188000");
            derive(MetalType.GOLD_18K, "0.735");

            DerivationUpdate again = derive(MetalType.GOLD_18K, "0.7350");

            assertThat(again.published()).isNull();
            assertThat(marketPriceService.history(MetalType.GOLD_18K, PriceSide.SELL,
                    org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("환산 중인 금속은 직접 고시할 수 없다")
        void derivedMetalCannotBeDeclared() {
            derive(MetalType.GOLD_18K, "0.735");

            assertThatThrownBy(() -> declarationService.declareBoth(MetalType.GOLD_18K,
                    new BigDecimal("160000"), new BigDecimal("140000"), "직접", ADMIN, true))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_METAL_DERIVED));
        }

        @Test
        @DisplayName("직접 입력으로 돌리면 마지막 환산값이 그대로 남고, 그다음부터 직접 고시할 수 있다")
        void switchingBackKeepsLastValue() {
            derive(MetalType.GOLD_18K, "0.735");
            declare("220800", "188000");

            DerivationUpdate update = policyService.updateDerivation(MetalType.GOLD_18K,
                    PolicyMode.DIRECT, null, "18K 따로 고시", ADMIN, false);

            assertThat(update.published()).isNull();
            assertThat(marketPriceService.describeCurrent(MetalType.GOLD_18K, PriceSide.SELL).isSellable())
                    .as("설정을 바꿨다고 시세문의로 빠지지 않는다")
                    .isTrue();
            assertThat(current(MetalType.GOLD_18K, PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("162288");

            declarationService.declareBoth(MetalType.GOLD_18K,
                    new BigDecimal("160000"), new BigDecimal("136000"), "18K 고시", ADMIN, false);
            assertThat(current(MetalType.GOLD_18K, PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("160000");
        }

        @Test
        @DisplayName("비율은 0 초과 1 이하, 소수 넷째 자리까지 — 금속은 18K·14K 만")
        void invalidSettingsAreRejected() {
            for (String ratio : List.of("0", "1.2", "0.12345")) {
                assertThatThrownBy(() -> derive(MetalType.GOLD_18K, ratio))
                        .as("비율 " + ratio)
                        .isInstanceOfSatisfying(BusinessException.class, e ->
                                assertThat(e.getFieldErrors()).containsKey("ratio"));
            }
            assertThatThrownBy(() -> policyService.updateDerivation(MetalType.GOLD_18K,
                    PolicyMode.DERIVED, null, "비율 없음", ADMIN, true))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> derive(MetalType.SILVER, "0.5"))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getFieldErrors()).containsKey("metalType"));

            assertThat(policy(MetalType.GOLD_18K, PriceSide.SELL).getMode()).isEqualTo(PolicyMode.DIRECT);
        }

        @Test
        @DisplayName("환산값이 금속 범위를 벗어나면 24K 고시까지 통째로 거절된다")
        void derivedRangeViolationRejectsWholeDeclaration() {
            derive(MetalType.GOLD_18K, "0.1");   // 24K 가 없어 설정만 저장된다

            // 220,800 × 0.1 = 22,080 — 18K 하한 30,000 미만
            assertThatThrownBy(() -> declare("220800", "188000"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_INVALID_VALUE);
                        assertThat(e.getFieldErrors()).containsKey("GOLD_18K/SELL");
                    });

            assertThat(count("market_prices_effective"))
                    .as("24K 만 나가고 18K 가 옛 기준으로 남는 순간을 만들지 않는다")
                    .isZero();
        }
    }

    // =================================================================
    // 도우미
    // =================================================================

    private QuotePublished declare(String sell, String buy) {
        return declarationService.declareBoth(MetalType.GOLD_24K,
                new BigDecimal(sell), new BigDecimal(buy), "테스트 고시", ADMIN, false);
    }

    private DerivationUpdate derive(MetalType metal, String ratio) {
        return policyService.updateDerivation(metal, PolicyMode.DERIVED, new BigDecimal(ratio),
                "환산 설정", ADMIN, true);
    }

    private MarketPriceEffective current(MetalType metal, PriceSide side) {
        return effectiveRepository.findByMetalTypeAndPriceSideAndEffectiveToIsNull(metal, side)
                .orElseThrow(() -> new AssertionError("현재 시세가 없습니다: " + metal + "/" + side));
    }

    private MarketPriceEffective previous(MetalType metal, PriceSide side) {
        return marketPriceService.findPreviousByKey().get(MarketPriceService.previousKey(metal, side));
    }

    private MarketPricePolicy policy(MetalType metal, PriceSide side) {
        return policyRepository.findByMetalTypeAndPriceSide(metal, side).orElseThrow();
    }

    private static QuoteLine line(QuotePlan plan, MetalType metal, PriceSide side) {
        return plan.lines().stream()
                .filter(l -> l.metalType() == metal && l.priceSide() == side)
                .findFirst()
                .orElseThrow(() -> new AssertionError("계획에 줄이 없습니다: " + metal + "/" + side));
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    /**
     * 현재 시세의 발행 시각을 과거로 되돌린다.
     *
     * <p>append-only 트리거가 마감 외의 UPDATE 를 막으므로 잠깐 내렸다 올린다.
     * <b>테스트에서만</b> 하는 일이다.
     */
    private void backdateCurrent(String interval) {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        try {
            jdbcTemplate.update("UPDATE market_prices_effective SET effective_from = now() - interval '"
                    + interval + "' WHERE effective_to IS NULL");
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
        }
        evictCaches();
    }

    private void evictCaches() {
        for (String name : List.of(CacheConfig.SYSTEM_SETTINGS, CacheConfig.MARKET_PRICES,
                CacheConfig.PRODUCT_PRICES)) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("JSON 파싱 실패: " + json, e);
        }
    }
}
