package com.hanseongge;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.entity.PolicyMode;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.provider.RawPrice;
import com.hanseongge.domain.marketprice.repository.MarketPriceEffectiveRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePendingRepository;
import com.hanseongge.domain.marketprice.repository.MarketPricePolicyRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceRawRepository;
import com.hanseongge.domain.marketprice.repository.MarketPriceSourceRepository;
import com.hanseongge.domain.marketprice.service.AutoQuoteService;
import com.hanseongge.domain.marketprice.service.MarketPriceCollectionWriter;
import com.hanseongge.domain.marketprice.service.MarketPricePolicyService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 자동고시 검증 — 외부 피드 수집값이 어떤 조건에서 판매가가 되는가.
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>살 때·팔 때가 <b>각각의 원본</b>으로 쌓이고, 각 쪽 정책이 제 원본에서 출발한다</li>
 *   <li>자동고시가 켜진 조합만 발행된다. 수동고시면 원본만 쌓이고 시세는 그대로다</li>
 *   <li>연동 대상 금속이 아니면 자동고시가 켜져 있어도 발행되지 않는다</li>
 *   <li>같은 고시시각은 두 번 적재되지 않는다 (10분마다 같은 값이 쌓이지 않는다)</li>
 *   <li>변동률 임계를 넘으면 <b>자동이어도</b> 발행 대신 승인 대기로 간다</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("자동고시 — 피드 수집값이 판매가가 되기까지")
class AutoQuoteIntegrationTest extends EmbeddedPostgresSupport {

    /** 자동 수집 원천 코드 (V14 가 넣고 V17 이 중립적인 이름으로 바꿨다). */
    private static final String FEED = "AUTO_FEED";

    @Autowired AutoQuoteService autoQuoteService;
    @Autowired MarketPriceCollectionWriter writer;
    @Autowired MarketPricePolicyService policyService;
    @Autowired com.hanseongge.domain.marketprice.service.MarketPriceService marketPriceService;
    @Autowired MarketPriceEffectiveRepository effectiveRepository;
    @Autowired MarketPriceRawRepository rawRepository;
    @Autowired MarketPricePolicyRepository policyRepository;
    @Autowired MarketPricePendingRepository pendingRepository;
    @Autowired MarketPriceSourceRepository sourceRepository;
    @Autowired SystemSettingService systemSettingService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void reset() {
        jdbcTemplate.execute("ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard");
        jdbcTemplate.execute("ALTER TABLE market_price_fetch_logs DISABLE TRIGGER trg_mpfl_guard");
        try {
            jdbcTemplate.update("DELETE FROM market_prices_effective");
            jdbcTemplate.update("DELETE FROM market_price_pending");
            jdbcTemplate.update("DELETE FROM market_price_fetch_logs");
            jdbcTemplate.update("DELETE FROM market_price_raw");
            jdbcTemplate.update(
                    "UPDATE market_price_policies SET mode='MANUAL', adj_amount=0, adj_percent=0, "
                            + "manual_price=NULL, auto_quote=FALSE, updated_by=NULL");
            jdbcTemplate.update(
                    "UPDATE system_settings SET value='GOLD_24K' WHERE key='PRICE_LINKED_METALS'");

            // 정책 행을 지우는 테스트가 있어 시드 상태로 되살린다. 지운 채로 두면
            // 그 다음에 도는 테스트가 "정책 없음"으로 무더기로 깨진다 —
            // 실제로 그렇게 깨뜨려 보고 넣은 코드다.
            for (String metal : new String[]{"GOLD_24K", "GOLD_18K", "GOLD_14K", "SILVER", "PLATINUM"}) {
                for (String side : new String[]{"SELL", "BUY"}) {
                    jdbcTemplate.update(
                            "INSERT INTO market_price_policies(metal_type, price_side, mode, "
                                    + "adj_amount, adj_percent, auto_quote, updated_at) "
                                    + "VALUES (?, ?, 'MANUAL', 0, 0, FALSE, now()) "
                                    + "ON CONFLICT (metal_type, price_side) DO NOTHING",
                            metal, side);
                }
            }
        } finally {
            jdbcTemplate.execute("ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard");
            jdbcTemplate.execute("ALTER TABLE market_price_fetch_logs ENABLE TRIGGER trg_mpfl_guard");
        }
        systemSettingService.evictAll();
    }

    // =================================================================

    @Nested
    @DisplayName("살 때와 팔 때가 각각의 원본으로 쌓인다")
    class SidedRaw {

        @Test
        @DisplayName("같은 고시시각의 두 side 가 서로를 밀어내지 않고 둘 다 저장된다")
        void bothSidesStored() {
            OffsetDateTime quotedAt = OffsetDateTime.parse("2026-09-09T09:53:38+09:00");

            // 828,000원/돈 ÷ 3.75 = 220,800원/g (살 때)
            // 705,000원/돈 ÷ 3.75 = 188,000원/g (팔 때)
            Long sellId = store(PriceSide.SELL, "220800", quotedAt);
            Long buyId = store(PriceSide.BUY, "188000", quotedAt);

            assertThat(sellId).isNotNull();
            assertThat(buyId)
                    .as("같은 고시시각이라도 side 가 다르면 별개의 원본이다")
                    .isNotNull()
                    .isNotEqualTo(sellId);

            List<MarketPriceRaw> all = rawRepository.findAll();
            assertThat(all).hasSize(2);
            assertThat(all).extracting(MarketPriceRaw::getPriceSide)
                    .containsExactlyInAnyOrder(PriceSide.SELL, PriceSide.BUY);
        }

        @Test
        @DisplayName("같은 side·같은 고시시각을 다시 받으면 적재하지 않는다")
        void duplicateQuoteIsSkipped() {
            OffsetDateTime quotedAt = OffsetDateTime.parse("2026-09-09T09:53:38+09:00");

            assertThat(store(PriceSide.SELL, "220800", quotedAt)).isNotNull();

            // 10분 뒤 수집기가 다시 돌았지만 저쪽은 아직 새 고시를 내지 않았다.
            assertThat(store(PriceSide.SELL, "220800", quotedAt))
                    .as("같은 고시를 두 번 적재하면 하루에 같은 값이 50행 쌓인다")
                    .isNull();

            assertThat(rawRepository.findAll()).hasSize(1);
        }
    }

    // =================================================================

    @Nested
    @DisplayName("자동고시 스위치")
    class QuoteMode {

        @Test
        @DisplayName("자동고시가 켜져 있으면 수집값에 정액 보정을 더해 즉시 발행한다")
        void autoQuotePublishes() {
            setPolicy(PriceSide.SELL, PolicyMode.FIXED_ADJ, new BigDecimal("3000"), true);

            Long rawId = store(PriceSide.SELL, "220800", now());
            boolean published = autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL, rawId);

            assertThat(published).isTrue();

            MarketPriceEffective current = current(PriceSide.SELL);
            assertThat(current.getPricePerGram())
                    .as("220,800 + 3,000 = 223,800")
                    .isEqualByComparingTo("223800.00");
            // 원본가는 이 행에 없다(D-11). base_raw_id 로 되짚는다 — 정보가 사라지지는 않는다.
            MarketPriceRaw base = rawRepository.findById(current.getBaseRawId()).orElseThrow();
            assertThat(base.getPricePerGram())
                    .as("원본은 보정 전 값 그대로 남는다")
                    .isEqualByComparingTo("220800.00");
        }

        @Test
        @DisplayName("수동고시면 원본만 쌓이고 시세는 움직이지 않는다")
        void manualQuoteDoesNotPublish() {
            setPolicy(PriceSide.SELL, PolicyMode.FIXED_ADJ, new BigDecimal("3000"), false);

            Long rawId = store(PriceSide.SELL, "220800", now());
            boolean published = autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL, rawId);

            assertThat(published).isFalse();
            assertThat(rawRepository.findAll())
                    .as("발행하지 않아도 원본은 남는다 — 관리자가 나중에 이 값으로 고시할 수 있어야 한다")
                    .hasSize(1);
            assertThat(effectiveRepository.findAll())
                    .as("수동고시 조합은 사람이 고시하기 전까지 시세가 없다")
                    .isEmpty();
        }

        @Test
        @DisplayName("한쪽만 자동으로 둘 수 없다 — 전환은 금속 단위이고 두 쪽이 함께 바뀐다")
        void quoteModeIsPerMetal() {
            policyService.switchQuoteMode(MetalType.GOLD_24K, true, "자동 시작", "admin");

            assertThat(policyRepository
                    .findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.SELL)
                    .orElseThrow().isAutoQuote()).isTrue();
            assertThat(policyRepository
                    .findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.BUY)
                    .orElseThrow().isAutoQuote())
                    .as("금값이 움직이면 두 값이 같이 움직여야 한다 — 한쪽만 자동인 상태를 만들 수 없다")
                    .isTrue();
        }
    }

    // =================================================================

    @Nested
    @DisplayName("연동 대상 금속 차단기")
    class LinkedMetals {

        @Test
        @DisplayName("연동 목록에 없는 금속은 자동고시가 켜져 있어도 발행되지 않는다")
        void unlistedMetalIsBlocked() {
            setPolicy(MetalType.SILVER, PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);

            Long rawId = store(MetalType.SILVER, PriceSide.SELL, "3184", now());
            boolean published = autoQuoteService.publish(MetalType.SILVER, PriceSide.SELL, rawId);

            assertThat(published)
                    .as("PRICE_LINKED_METALS 가 GOLD_24K 뿐이므로 은은 막힌다")
                    .isFalse();
            assertThat(effectiveRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("설정이 비어 있으면 전부 차단된다 — 모르는 값에서 전체 허용으로 넘어가지 않는다")
        void emptySettingBlocksEverything() {
            jdbcTemplate.update("UPDATE system_settings SET value='' WHERE key='PRICE_LINKED_METALS'");
            systemSettingService.evictAll();

            assertThat(autoQuoteService.linkedMetals()).isEmpty();

            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);
            Long rawId = store(PriceSide.SELL, "220800", now());

            assertThat(autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL, rawId)).isFalse();
        }

        @Test
        @DisplayName("오타가 섞여 있어도 나머지는 살아 있고, 오타만 무시된다")
        void unknownTokenIsIgnored() {
            jdbcTemplate.update(
                    "UPDATE system_settings SET value='GOLD_24K, GOLD_9K ,SILVER' "
                            + "WHERE key='PRICE_LINKED_METALS'");
            systemSettingService.evictAll();

            assertThat(autoQuoteService.linkedMetals())
                    .containsExactlyInAnyOrder(MetalType.GOLD_24K, MetalType.SILVER);
        }
    }

    // =================================================================

    @Nested
    @DisplayName("금값이 움직이면 살 때·팔 때가 함께 움직인다")
    class MovesTogether {

        @Test
        @DisplayName("자동고시는 두 쪽을 같은 시각에 함께 발행한다")
        void publishesBothAtSameInstant() {
            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);

            OffsetDateTime at = now();
            boolean ok = autoQuoteService.publishPair(MetalType.GOLD_24K,
                    store(PriceSide.SELL, "220800", at),
                    store(PriceSide.BUY, "188000", at));

            assertThat(ok).isTrue();
            assertThat(current(PriceSide.SELL).getEffectiveFrom())
                    .as("발행 시각이 같아야 이력 화면이 한 줄로 묶는다")
                    .isEqualTo(current(PriceSide.BUY).getEffectiveFrom());
        }

        @Test
        @DisplayName("한쪽이 임계를 넘으면 둘 다 보류한다 — 통과한 쪽만 나가면 시세표가 어긋난다")
        void oneSpikeHoldsBoth() {
            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);

            OffsetDateTime base = now().minusHours(1);
            autoQuoteService.publishPair(MetalType.GOLD_24K,
                    store(PriceSide.SELL, "220800", base),
                    store(PriceSide.BUY, "188000", base));

            // 살 때만 두 배로 튄다. 팔 때는 정상 범위다.
            OffsetDateTime at = now();
            boolean ok = autoQuoteService.publishPair(MetalType.GOLD_24K,
                    store(PriceSide.SELL, "441600", at),
                    store(PriceSide.BUY, "188100", at));

            assertThat(ok).isFalse();
            assertThat(current(PriceSide.SELL).getPricePerGram())
                    .isEqualByComparingTo("220800.00");
            assertThat(current(PriceSide.BUY).getPricePerGram())
                    .as("정상 범위였어도 반대쪽이 보류면 함께 멈춘다")
                    .isEqualByComparingTo("188000.00");
            assertThat(pendingRepository.findAll())
                    .as("두 쪽 모두 승인 대기로 넘어간다")
                    .filteredOn(x -> x.getStatus() == PendingStatus.PENDING)
                    .hasSize(2);
        }

        @Test
        @DisplayName("한쪽 원본이 없으면 아무것도 발행하지 않는다")
        void missingOneSidePublishesNothing() {
            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);

            assertThat(autoQuoteService.publishPair(MetalType.GOLD_24K,
                    store(PriceSide.SELL, "220800", now()), null)).isFalse();
            assertThat(effectiveRepository.findAll())
                    .as("반대쪽 없이 한쪽만 내면 바로 그 어긋난 상태가 된다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("자동이어도 안전장치는 그대로다")
    class SafetyHolds {

        @Test
        @DisplayName("변동률 임계를 넘으면 발행하지 않고 승인 대기로 간다 — 기존 시세가 유지된다")
        void spikeGoesToPending() {
            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);

            // 첫 고시로 기준을 만든다.
            autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL,
                    store(PriceSide.SELL, "220800", now().minusHours(1)));
            assertThat(current(PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("220800.00");

            // 피드가 이상한 값을 준 상황. 발주 요구의 "사고가 터질 시"가 이것이다.
            Long spikeId = store(PriceSide.SELL, "441600", now());
            boolean published = autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL, spikeId);

            assertThat(published).isFalse();
            assertThat(current(PriceSide.SELL).getPricePerGram())
                    .as("판매가는 직전 값 그대로다 — 자동이라고 검증을 건너뛰지 않는다")
                    .isEqualByComparingTo("220800.00");
            assertThat(pendingRepository.findAll())
                    .as("사람이 판단하도록 승인 대기로 넘어간다")
                    .anyMatch(p -> p.getStatus() == PendingStatus.PENDING);
        }

        @Test
        @DisplayName("정책 행이 없으면 발행하지 않는다 — 보정 없이 원본이 그대로 나가지 않는다")
        void missingPolicyBlocks() {
            // GOLD_24K/SELL 정책을 지운다. 시드에는 있지만, 없을 때의 동작을 고정해 둔다.
            policyRepository.findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.SELL)
                    .ifPresent(policyRepository::delete);

            Long rawId = store(PriceSide.SELL, "220800", now());
            assertThat(autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL, rawId)).isFalse();
            assertThat(effectiveRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("rawId 가 null 이면 (검증 탈락·중복) 조용히 아무것도 하지 않는다")
        void nullRawIsNoop() {
            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);
            assertThat(autoQuoteService.publish(MetalType.GOLD_24K, PriceSide.SELL, null)).isFalse();
        }
    }

    // =================================================================

    @Nested
    @DisplayName("0원 이하가 되는 보정값")
    class NonPositiveResult {

        @Test
        @DisplayName("저장 시점에 막는다 — 고시할 때가 아니라")
        void rejectedAtSaveTime() {
            store(PriceSide.SELL, "220800", now());

            assertThatThrownBy(() -> policyService.update(MetalType.GOLD_24K, PriceSide.SELL,
                    PolicyMode.FIXED_ADJ, new BigDecimal("-300000"), BigDecimal.ZERO, null,
                    "실수", "admin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("0원 이하");
        }

        @Test
        @DisplayName("거절 메시지에 원본·보정값·결과가 모두 들어 있다")
        void messageExplainsWhy() {
            store(PriceSide.SELL, "220800", now());

            assertThatThrownBy(() -> policyService.update(MetalType.GOLD_24K, PriceSide.SELL,
                    PolicyMode.FIXED_ADJ, new BigDecimal("-300000"), BigDecimal.ZERO, null,
                    "실수", "admin"))
                    .hasMessageContaining("220800")
                    .hasMessageContaining("-300000");
        }

        @Test
        @DisplayName("자동고시를 켤 때도 같은 검산을 한다")
        void autoQuoteStartAlsoChecked() {
            store(PriceSide.SELL, "220800", now());
            store(PriceSide.BUY, "188000", now());

            assertThatThrownBy(() -> policyService.startAutoQuote(MetalType.GOLD_24K,
                    PolicyMode.FIXED_ADJ, new BigDecimal("-300000"), new BigDecimal("-1500"),
                    "실수", "admin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("0원 이하");
        }

        @Test
        @DisplayName("원본이 아직 없으면 통과시킨다 — 비교할 대상이 없다")
        void noRawPasses() {
            policyService.update(MetalType.PLATINUM, PriceSide.SELL, PolicyMode.FIXED_ADJ,
                    new BigDecimal("-300000"), BigDecimal.ZERO, null, "선설정", "admin");

            assertThat(policyRepository
                    .findByMetalTypeAndPriceSide(MetalType.PLATINUM, PriceSide.SELL)
                    .orElseThrow().getAdjAmount()).isEqualByComparingTo("-300000");
        }
    }

    @Nested
    @DisplayName("바뀐 보정값을 지금 시세에 다시 적용")
    class Reapply {

        @Test
        @DisplayName("보정값을 바꾸고 재적용하면 새 값으로 다시 발행된다")
        void reappliesWithNewAdjustment() {
            setPolicy(PriceSide.SELL, PolicyMode.FIXED_ADJ, new BigDecimal("3000"), true);
            setPolicy(PriceSide.BUY, PolicyMode.FIXED_ADJ, new BigDecimal("-1500"), true);

            OffsetDateTime at = now();
            autoQuoteService.publishPair(MetalType.GOLD_24K,
                    store(PriceSide.SELL, "220800", at),
                    store(PriceSide.BUY, "188000", at));
            assertThat(current(PriceSide.SELL).getPricePerGram()).isEqualByComparingTo("223800.00");

            // 마진을 올린다. 이 시점에는 아직 발행된 시세가 그대로다.
            policyService.update(MetalType.GOLD_24K, PriceSide.SELL, PolicyMode.FIXED_ADJ,
                    new BigDecimal("5000"), BigDecimal.ZERO, null, "마진 인상", "admin");
            assertThat(current(PriceSide.SELL).getPricePerGram())
                    .as("보정값만 바꿔서는 이미 나간 시세가 바뀌지 않는다")
                    .isEqualByComparingTo("223800.00");

            marketPriceService.reapplyCurrentPolicy(MetalType.GOLD_24K, "마진 즉시 반영", "admin");

            assertThat(current(PriceSide.SELL).getPricePerGram())
                    .as("220,800 + 5,000")
                    .isEqualByComparingTo("225800.00");
        }

        @Test
        @DisplayName("원본이 노후 한계보다 오래되면 거절한다 — 옛 값이 방금 것처럼 되살아나면 안 된다")
        void staleRawRejected() {
            setPolicy(PriceSide.SELL, PolicyMode.AUTO, BigDecimal.ZERO, true);

            // 노후 한계(기본 240분)를 훌쩍 넘긴 원본만 있는 상태
            OffsetDateTime old = now().minusDays(2);
            store(PriceSide.SELL, "220800", old);
            store(PriceSide.BUY, "188000", old);

            assertThatThrownBy(() -> marketPriceService.reapplyCurrentPolicy(
                    MetalType.GOLD_24K, "재적용 시도", "admin"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("오래되었습니다");
        }
    }

    @Nested
    @DisplayName("전환 API")
    class SwitchApi {

        @Test
        @DisplayName("자동고시를 켜면서 보정값을 함께 정한다 — 값 없이 켜지는 길이 없다")
        void startsWithAdjustment() {
            policyService.startAutoQuote(MetalType.GOLD_24K, PolicyMode.FIXED_ADJ,
                    new BigDecimal("3000"), new BigDecimal("-1500"), "오전 마진", "admin");

            MarketPricePolicy sell = policyRepository
                    .findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.SELL).orElseThrow();
            MarketPricePolicy buy = policyRepository
                    .findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.BUY).orElseThrow();

            assertThat(sell.isAutoQuote()).isTrue();
            assertThat(buy.isAutoQuote()).isTrue();
            assertThat(sell.getAdjAmount()).isEqualByComparingTo("3000");
            assertThat(buy.getAdjAmount())
                    .as("매입은 빼는 쪽이다 — 음수도 그대로 저장된다")
                    .isEqualByComparingTo("-1500");
        }

        @Test
        @DisplayName("켜면서 정한 값이 곧바로 다음 고시에 적용된다")
        void adjustmentAppliesImmediately() {
            policyService.startAutoQuote(MetalType.GOLD_24K, PolicyMode.FIXED_ADJ,
                    new BigDecimal("3000"), new BigDecimal("-1500"), "오전 마진", "admin");

            OffsetDateTime at = now();
            autoQuoteService.publishPair(MetalType.GOLD_24K,
                    store(PriceSide.SELL, "220800", at),
                    store(PriceSide.BUY, "188000", at));

            assertThat(current(PriceSide.SELL).getPricePerGram())
                    .as("220,800 + 3,000")
                    .isEqualByComparingTo("223800.00");
            assertThat(current(PriceSide.BUY).getPricePerGram())
                    .as("188,000 - 1,500")
                    .isEqualByComparingTo("186500.00");
        }

        @Test
        @DisplayName("지정가로는 자동고시를 켤 수 없다 — 수집값을 받아 놓고 무시하는 셈이다")
        void manualModeRejected() {
            assertThatThrownBy(() -> policyService.startAutoQuote(MetalType.GOLD_24K,
                    PolicyMode.MANUAL, new BigDecimal("200000"), new BigDecimal("180000"),
                    "실수", "admin"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("보정값을 건드리지 않고 자동/수동만 바꾼다")
        void switchKeepsAdjustment() {
            policyService.update(MetalType.GOLD_24K, PriceSide.SELL, PolicyMode.FIXED_ADJ,
                    new BigDecimal("3000"), BigDecimal.ZERO, null, "마진 설정", "admin");

            policyService.switchQuoteMode(MetalType.GOLD_24K, true, "자동고시 시작", "admin");

            MarketPricePolicy policy = policyRepository
                    .findByMetalTypeAndPriceSide(MetalType.GOLD_24K, PriceSide.SELL).orElseThrow();

            assertThat(policy.isAutoQuote()).isTrue();
            assertThat(policy.getMode())
                    .as("스위치만 내렸는데 보정 방식이 초기화되면 급할 때 사고가 난다")
                    .isEqualTo(PolicyMode.FIXED_ADJ);
            assertThat(policy.getAdjAmount()).isEqualByComparingTo("3000");
        }
    }

    // =================================================================
    // 헬퍼
    // =================================================================

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private Long store(PriceSide side, String pricePerGram, OffsetDateTime quotedAt) {
        return store(MetalType.GOLD_24K, side, pricePerGram, quotedAt);
    }

    /** 수집기가 피드에서 한 건 받아 저장한 상황을 그대로 재현한다. */
    private Long store(MetalType metal, PriceSide side, String pricePerGram, OffsetDateTime quotedAt) {
        MarketPriceSource feed = sourceRepository.findByCode(FEED).orElseThrow();
        RawPrice raw = new RawPrice(metal, PricePerGram.of(new BigDecimal(pricePerGram)),
                quotedAt, "{\"test\":true}");
        return writer.store(feed, metal, side, raw, 12, 1, OffsetDateTime.now());
    }

    private void setPolicy(PriceSide side, PolicyMode mode, BigDecimal adj, boolean autoQuote) {
        setPolicy(MetalType.GOLD_24K, side, mode, adj, autoQuote);
    }

    private void setPolicy(MetalType metal, PriceSide side, PolicyMode mode,
                           BigDecimal adj, boolean autoQuote) {
        policyService.update(metal, side, mode, adj, BigDecimal.ZERO, null, "테스트 설정", "admin");
        // 고시 방식은 금속 단위다 — 살 때만 자동으로 둘 수 없다.
        policyService.switchQuoteMode(metal, autoQuote, "테스트 설정", "admin");
    }

    private MarketPriceEffective current(PriceSide side) {
        return effectiveRepository
                .findByMetalTypeAndPriceSideAndEffectiveToIsNull(MetalType.GOLD_24K, side)
                .orElseThrow(() -> new AssertionError("현재 시세가 없습니다: " + side));
    }
}
