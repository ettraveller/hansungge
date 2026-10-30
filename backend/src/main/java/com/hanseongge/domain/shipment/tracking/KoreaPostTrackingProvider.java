package com.hanseongge.domain.shipment.tracking;

import com.hanseongge.domain.shipment.entity.Carrier;
import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 우체국 배송조회 — 공공데이터포털 「국내우편물 종적 조회 서비스」.
 *
 * <h2>응답을 고정된 경로로 읽지 않는다</h2>
 * XML 의 중첩 구조를 경로로 박아 두면 우정사업본부가 래퍼를 한 겹 바꾸는 날 전부
 * 빈 결과가 된다. 그런데 그때 돌아오는 것은 오류가 아니라 <b>단계 0개</b>라서,
 * "아직 집화 전인가" 와 구분되지 않는다. 그래서 태그 이름으로 긁어 모은다.
 *
 * <h2>실패해도 배송을 막지 않는다</h2>
 * 조회가 안 되는 것은 기술적 실패다. 예외를 던지고 부르는 쪽이 다음 주기에 다시
 * 묻는다 — 송장번호와 조회 링크는 그대로 있으므로 고객은 직접 확인할 수 있다.
 *
 * <h2>수령을 확정하지 않는다</h2>
 * 택배사가 "배달완료"라고 해도 {@code delivered} 를 신호로만 돌려준다. 고가 귀금속의
 * 수령 확인은 사람이 남기는 기록이어야 분쟁에서 근거가 된다 (이용약관 제10조).
 */
@Slf4j
@Component
public class KoreaPostTrackingProvider implements DeliveryTrackingProvider {

    private static final String BASE_URL =
            "https://openapi.epost.go.kr/trace/retrieveLongitudinalService"
                    + "/retrieveLongitudinalService/getLongitudinalDomesticList";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 배달이 끝났다고 보는 표현.
     *
     * <p>우체국은 {@code processSttus} 에 "배달완료"를 쓰지만, 수취인 부재·반송처럼
     * 끝났는데 완료가 아닌 경우가 있다. 그래서 완료만 따로 보고 나머지는 전부
     * "가는 중"으로 둔다 — 배달 중이 아닌 것을 배달 중이라 말하는 쪽이,
     * 끝난 것을 안 끝났다고 말하는 쪽보다 덜 위험하다.
     */
    private static final String DELIVERED_MARK = "배달완료";

    private final AppProperties.Tracking config;
    private final RestClient restClient;

    public KoreaPostTrackingProvider(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.tracking();
        this.restClient = builder.build();

        if (!config.configured()) {
            log.info("[TRACKING] 우체국 배송조회 키가 없어 조회를 켜지 않는다. "
                    + "송장번호와 우체국 조회 링크는 그대로 동작한다 "
                    + "(공공데이터포털 「국내우편물 종적 조회 서비스」 활용신청 필요)");
        }
    }

    @Override
    public boolean isConfigured() {
        return config.configured();
    }

    @Override
    public TrackingResult track(Carrier carrier, String trackingNo) {
        if (carrier != Carrier.KOREA_POST) {
            throw new DeliveryTrackingException(
                    "우체국 조회기가 다른 택배사를 받았다: " + carrier);
        }
        if (!isConfigured()) {
            throw new DeliveryTrackingException("배송조회 키가 설정되지 않았다");
        }

        List<TrackingResult.Step> steps = parseSteps(fetch(trackingNo));

        if (steps.isEmpty()) {
            // 단계가 하나도 없는 것은 "아직 집화 전"일 수도, 번호가 틀린 것일 수도 있다.
            // 둘을 구분할 방법이 없어 실패로 두지 않는다 — 실패로 두면 접수 직후
            // 몇 시간 동안 매 주기마다 오류 로그가 쌓인다.
            log.debug("[TRACKING] 진행 단계 없음 trackingNo={}", trackingNo);
            return new TrackingResult(null, false, false, List.of());
        }

        TrackingResult.Step last = steps.get(steps.size() - 1);
        boolean delivered = last.what() != null && last.what().contains(DELIVERED_MARK);

        return new TrackingResult(last.what(), !delivered, delivered, steps);
    }

    // =================================================================
    // 호출
    // =================================================================

    /**
     * 응답을 <b>바이트로</b> 받는다. 문자열로 받지 않는다.
     *
     * <p>{@code String} 으로 받으면 Spring 이 Content-Type 의 charset 으로 디코딩하는데,
     * charset 이 없는 {@code application/xml} 은 ISO-8859-1 로 읽힌다 — 그러면 "집화완료"가
     * 깨진 글자로 들어오고, 그대로 DB 에 저장되어 고객 화면까지 깨진 채로 나간다.
     *
     * <p>바이트로 넘기면 XML 파서가 선언부({@code <?xml encoding=...?>})를 보고 스스로
     * 정한다. 우정사업본부가 인코딩을 바꿔도 따라간다.
     */
    private byte[] fetch(String trackingNo) {
        /*
         * serviceKey 를 Decoding 키로 받아 여기서 한 번만 인코딩한다.
         * Encoding 키를 넣으면 `%2B` 의 `%` 가 다시 인코딩되어 `%252B` 가 되는데,
         * 서버는 "키가 틀렸다"가 아니라 빈 결과를 돌려줘서 원인을 찾기 어렵다.
         */
        String uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("serviceKey", config.serviceKey())
                .queryParam("rgist", trackingNo)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        try {
            byte[] body = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new DeliveryTrackingException("우체국 응답이 비어 있다");
            }
            return body;
        } catch (DeliveryTrackingException e) {
            throw e;
        } catch (Exception e) {
            // 키·주소를 로그에 남기지 않는다. serviceKey 가 uri 에 들어 있다.
            throw new DeliveryTrackingException("우체국 배송조회 호출 실패: " + e.getMessage(), e);
        }
    }

    // =================================================================
    // 파싱
    // =================================================================

    /**
     * 진행 단계를 긁어 모은다.
     *
     * <p>{@code detailDc} 를 가진 노드를 전부 찾아 그 형제에서 날짜·시각·장소·상태를
     * 읽는다. 바깥 래퍼가 무엇이든 상관없다.
     */
    private List<TrackingResult.Step> parseSteps(byte[] xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // XXE 차단. 외부 응답을 파싱하는 곳이라 반드시 끈다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            // 바이트로 넘긴다 — 파서가 XML 선언부에서 인코딩을 읽는다 (없으면 UTF-8).
            var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

            List<TrackingResult.Step> steps = new ArrayList<>();
            NodeList marks = doc.getElementsByTagName("detailDc");

            for (int i = 0; i < marks.getLength(); i++) {
                Node parent = marks.item(i).getParentNode();
                if (!(parent instanceof Element row)) {
                    continue;
                }
                String what = text(row, "processSttus");
                if (what == null || what.isBlank()) {
                    what = text(row, "detailDc");
                }
                steps.add(new TrackingResult.Step(
                        at(text(row, "dlvyDate"), text(row, "dlvyTime")),
                        text(row, "nowLc"),
                        what));
            }
            return steps;
        } catch (Exception e) {
            throw new DeliveryTrackingException("우체국 응답을 읽지 못했다: " + e.getMessage(), e);
        }
    }

    private static String text(Element row, String tag) {
        NodeList found = row.getElementsByTagName(tag);
        if (found.getLength() == 0) {
            return null;
        }
        String value = found.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * {@code 2026.10.30} + {@code 17:44} 를 시각으로.
     *
     * <p>읽지 못하면 null 을 돌려준다. 시각 하나 때문에 단계 전체를 버리면, 화면에
     * "아직 아무 일도 없음"으로 보인다 — 실제로는 가고 있는데.
     */
    private static OffsetDateTime at(String date, String time) {
        if (date == null) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(date.trim(), DATE);
            LocalTime t = time == null ? LocalTime.MIDNIGHT : LocalTime.parse(time.trim(), TIME);
            return LocalDateTime.of(d, t).atZone(KST).toOffsetDateTime();
        } catch (Exception e) {
            log.debug("[TRACKING] 시각을 읽지 못했다 date={} time={}", date, time);
            return null;
        }
    }
}
