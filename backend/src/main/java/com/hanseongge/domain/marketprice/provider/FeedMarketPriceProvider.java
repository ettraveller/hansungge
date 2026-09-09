package com.hanseongge.domain.marketprice.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.global.util.PricePerGram;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 외부 고시 피드 수집기 — <b>살 때와 팔 때를 각각</b> 가져온다.
 *
 * <h2>이 공급자가 다른 이유</h2>
 * {@link MarketPriceProvider#fetch(MetalType)} 은 금속 하나에 값 하나를 돌려주는 계약이다.
 * 그 계약은 "국제 기준가 하나 + 우리 마진"인 원천(KRX 등)에 맞는다. 이 피드는 다르다 —
 * 소매 고시가라서 살 때와 팔 때가 <b>애초에 별개의 숫자</b>이고, 둘 사이 간격도 날마다
 * 흔들린다(고정 마진이 아니다). 그래서 {@link #fetchBothSides(MetalType)} 를 따로 둔다.
 * 기존 {@code fetch} 는 살 때 값을 돌려주어 계약을 깨지 않는다.
 *
 * <h2>단위</h2>
 * 피드는 <b>1돈(3.75g)</b> 단위로 고시한다. 우리 저장 단위는 g 다. 나누는 값을 코드에
 * 박지 않고 {@code system_settings.FEED_UNIT_GRAMS} 로 두는 이유는, 공급자가 바뀌면
 * 그 값도 바뀌는데 그때 재배포를 기다리는 동안 시세가 멈추기 때문이다.
 *
 * <h2>파생 금속을 굳이 받는 이유</h2>
 * 피드의 18K/14K 는 순금에서 기계적으로 나온다 — 실측으로 확인했다:
 * <pre>
 *   828,000 × 0.735 = 608,580 = 18K 살 때   (정확히 일치)
 *   828,000 × 0.57  = 471,960 = 14K 살 때   (정확히 일치)
 * </pre>
 * 우리가 같은 비율로 계산해도 같은 값이 나온다. 그래도 <b>받아서 저장한다</b> —
 * 저쪽이 비율을 바꾸면 우리 계산과 어긋나는데, 원본을 남겨두지 않으면 그 사실을
 * 발견할 방법이 없다. 저장은 하되 쓸지 말지는 정책(L2)이 정한다.
 *
 * <h2>실패</h2>
 * 조용히 빈 값을 돌려주지 않는다. {@link MarketPriceFetchException} 으로 종류를 구분해
 * 던지면 그것이 그대로 수집 로그가 되고, 마지막 정상 시세는 유지된다(원칙 P6).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.market-price.feed.enabled", havingValue = "true")
public class FeedMarketPriceProvider implements MarketPriceProvider {

    /** {@code market_price_sources.code}. V14 가 넣어 둔 행과 맞아야 한다. */
    public static final String CODE = "KGX";

    /** 고시시각은 KST 로 온다. 서버 시간대와 무관하게 해석이 같아야 한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter QUOTED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter REQUEST_DATE =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 응답 필드 이름. {@code s_} = 살 때, {@code p_} = 팔 때.
     *
     * <p>백금이 {@code white} 인 것은 저쪽 명명이다. 우리 쪽 이름으로 고쳐 부르지 않는다 —
     * 응답을 눈으로 대조할 때 이름이 다르면 매번 표를 다시 그려야 한다.
     */
    private static final Map<MetalType, String> FIELD = new EnumMap<>(Map.of(
            MetalType.GOLD_24K, "pure",
            MetalType.GOLD_18K, "18k",
            MetalType.GOLD_14K, "14k",
            MetalType.SILVER, "silver",
            MetalType.PLATINUM, "white"
    ));

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String feedType;
    private final BigDecimal unitGrams;
    private final Duration timeout;

    public FeedMarketPriceProvider(
            ObjectMapper mapper,
            @Value("${app.market-price.feed.endpoint}") String endpoint,
            @Value("${app.market-price.feed.type:Au}") String feedType,
            @Value("${app.market-price.feed.unit-grams:3.75}") BigDecimal unitGrams,
            @Value("${app.market-price.feed.timeout-seconds:8}") long timeoutSeconds) {
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.feedType = feedType;
        this.unitGrams = unitGrams;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                // 리다이렉트를 따라가지 않는다. 응답이 조용히 로그인 페이지로 바뀌면
                // 파싱 실패로 드러나야지, 엉뚱한 HTML 을 200 으로 받아서는 안 된다.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String sourceCode() {
        return CODE;
    }

    @Override
    public boolean pollable() {
        return true;
    }

    @Override
    public List<MetalType> supportedMetals() {
        return List.copyOf(FIELD.keySet());
    }

    /**
     * 계약 유지용. 한 값만 요구하는 호출자에게는 <b>살 때</b>를 준다.
     *
     * <p>판매가가 이 쇼핑몰의 주 용도이기 때문이다. 양쪽이 다 필요하면
     * {@link #fetchBothSides(MetalType)} 를 쓴다.
     */
    @Override
    public Optional<RawPrice> fetch(MetalType metal) {
        return fetchBothSides(metal).stream()
                .filter(q -> q.side() == PriceSide.SELL)
                .map(SidedPrice::raw)
                .findFirst();
    }

    /**
     * 살 때·팔 때를 <b>한 번의 요청으로</b> 가져온다.
     *
     * <p>금속마다 따로 부르지 않는 것이 중요하다. 응답 한 건에 5개 금속이 모두 들어
     * 있는데 금속별로 요청하면 같은 데이터를 5번 받는다 — 우리 쪽 낭비이자
     * 상대 서버에 대한 결례다. 호출자는 금속별로 부르지만, 응답은 캐시하지 않고도
     * 한 번의 왕복으로 끝나도록 이 메서드가 두 side 를 함께 돌려준다.
     */
    public List<SidedPrice> fetchBothSides(MetalType metal) {
        String field = FIELD.get(metal);
        if (field == null) {
            return List.of();
        }

        String body = requestBody();
        JsonNode latest = latestRow(callFeed(body));

        OffsetDateTime quotedAt = parseQuotedAt(latest);
        String payload = latest.toString();

        List<SidedPrice> out = new ArrayList<>(2);
        addIfPresent(out, latest, "s_" + field, metal, PriceSide.SELL, quotedAt, payload);
        addIfPresent(out, latest, "p_" + field, metal, PriceSide.BUY, quotedAt, payload);

        if (out.isEmpty()) {
            throw new MarketPriceFetchException(FetchResult.PARSE_ERROR,
                    "응답에 %s 의 값이 없습니다 (s_%s / p_%s)".formatted(metal, field, field));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 내부
    // ------------------------------------------------------------------

    /**
     * 조회 구간은 <b>어제부터 오늘까지</b>다.
     *
     * <p>오늘 하루만 요청하면 장이 열리기 전이나 휴일 아침에 빈 배열이 온다. 그때
     * "수집 실패"로 기록하면 매일 아침 거짓 경보가 울린다. 어제를 포함하면 직전
     * 고시가가 잡히고, 그 값이 오래되었는지는 {@code PRICE_STALE_MINUTES} 가 판단한다 —
     * 그것이 원래 그 설정의 일이다.
     */
    private String requestBody() {
        LocalDate today = LocalDate.now(KST);
        return """
                {"srchDt":"","type":"%s","dataDateStart":"%s","dataDateEnd":"%s"}"""
                .formatted(feedType,
                        today.minusDays(1).format(REQUEST_DATE),
                        today.format(REQUEST_DATE));
    }

    private JsonNode callFeed(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new MarketPriceFetchException(FetchResult.TIMEOUT,
                    "피드 응답 시간 초과 (%s초)".formatted(timeout.toSeconds()));
        } catch (IOException e) {
            throw new MarketPriceFetchException(FetchResult.HTTP_ERROR,
                    "피드 연결 실패: " + e.getMessage());
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 이 스레드를 멈추려는 쪽이 영영 멈추지 못한다.
            Thread.currentThread().interrupt();
            throw new MarketPriceFetchException(FetchResult.EXCEPTION, "수집이 중단되었습니다");
        }

        if (response.statusCode() != 200) {
            throw new MarketPriceFetchException(FetchResult.HTTP_ERROR,
                    "피드 HTTP %d".formatted(response.statusCode()));
        }

        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new MarketPriceFetchException(FetchResult.PARSE_ERROR,
                    "피드 응답이 JSON 이 아닙니다: " + head(response.body()));
        }
    }

    /** 목록은 최신이 먼저다. 그 순서를 믿지 않고 고시시각으로 직접 고른다. */
    private JsonNode latestRow(JsonNode root) {
        JsonNode list = root.path("list");
        if (!list.isArray() || list.isEmpty()) {
            throw new MarketPriceFetchException(FetchResult.PARSE_ERROR,
                    "피드에 고시 내역이 없습니다");
        }

        JsonNode best = null;
        LocalDateTime bestAt = null;
        for (JsonNode row : list) {
            LocalDateTime at = parseLocal(row.path("date").asText(null));
            if (at == null) continue;
            if (bestAt == null || at.isAfter(bestAt)) {
                bestAt = at;
                best = row;
            }
        }
        if (best == null) {
            throw new MarketPriceFetchException(FetchResult.PARSE_ERROR,
                    "피드 고시시각을 읽지 못했습니다");
        }
        return best;
    }

    private void addIfPresent(List<SidedPrice> out, JsonNode row, String field, MetalType metal,
                              PriceSide side, OffsetDateTime quotedAt, String payload) {
        JsonNode node = row.get(field);
        if (node == null || !node.isNumber()) {
            return;
        }
        BigDecimal perUnit = node.decimalValue();
        if (perUnit.signum() <= 0) {
            // 0 이나 음수는 "값이 없다"는 뜻이지 "0원"이 아니다. 통과시키면
            // 원칙 P6(가격을 0 으로 만들지 않는다)이 이 자리에서 깨진다.
            log.warn("[MARKET-PRICE] 피드 {} {} 값이 {} 입니다 — 건너뜁니다", metal, side, perUnit);
            return;
        }

        // 1돈 → 1g. 나눗셈이라 반드시 자릿수를 정해야 한다(무한소수면 예외가 난다).
        BigDecimal perGram = perUnit.divide(unitGrams, MathContext.DECIMAL64);
        out.add(new SidedPrice(side,
                new RawPrice(metal, PricePerGram.of(perGram), quotedAt, payload)));
    }

    private OffsetDateTime parseQuotedAt(JsonNode row) {
        LocalDateTime at = parseLocal(row.path("date").asText(null));
        if (at == null) {
            throw new MarketPriceFetchException(FetchResult.PARSE_ERROR, "고시시각 형식이 다릅니다");
        }
        // 수집시각이 아니라 고시시각을 쓴다. 이 값이 중복 판정의 기준이라,
        // 수집시각을 넣으면 같은 고시가 매 주기 새 행으로 쌓인다.
        return at.atZone(KST).toOffsetDateTime();
    }

    private static LocalDateTime parseLocal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text.trim(), QUOTED_AT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 오류 메시지에 응답 전문을 붙이면 로그가 못 쓰게 된다. 앞부분만 남긴다. */
    private static String head(String body) {
        if (body == null) return "(빈 응답)";
        String flat = body.strip().replaceAll("\\s+", " ");
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }

    /** 어느 쪽 값인지 함께 들고 다니는 원본. */
    public record SidedPrice(PriceSide side, RawPrice raw) {
    }
}
