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
import java.net.URI;
import java.net.URLDecoder;
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

    /**
     * ⚠️ {@code http} 다. {@code https} 가 아니다.
     *
     * <p>우정사업본부 서버가 <b>443 포트를 열지 않는다</b> — 연결 자체가 타임아웃이다
     * (2026-10-30 확인). https 로 두면 매 조회가 20초를 기다렸다 실패하고, 배치가
     * 한 건당 20초씩 밀린다.
     *
     * <p>그래서 인증키가 평문 질의문자열로 나간다. 이 키는 <b>읽기 전용 공개데이터
     * 키</b>라서 새어도 남이 쓸 수 있는 것은 하루 조회 한도뿐이다 — 우리 데이터에
     * 닿지 않는다. 그 전제가 깨지는 API 를 같은 방식으로 붙이지 않는다.
     */
    private static final String BASE_URL =
            "http://openapi.epost.go.kr/trace/retrieveLongitudinalService"
                    + "/retrieveLongitudinalService/getLongitudinalDomesticList";

    /**
     * 「조회결과가 없습니다」.
     *
     * <p>아직 집화 전이거나 번호가 틀렸을 때 온다. 둘을 구분할 방법이 없어 실패로 두지
     * 않는다 — 실패로 두면 접수 직후 몇 시간 동안 매 주기마다 오류 로그가 쌓인다.
     */
    private static final String NO_RESULT_CODE = "03";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /**
     * 날짜 구분자를 믿지 않는다.
     *
     * <p>규격서 표는 {@code yyyy.mm.dd} 라고 적어 두고, <b>같은 문서의 샘플 응답은
     * {@code 2017-09-12} 를 보여준다.</b> 둘 중 어느 쪽이 실제인지 규격서로는 알 수
     * 없고, 운영 중에 바뀔 수도 있다.
     *
     * <p>한쪽만 맞춰 두면 시각이 전부 null 이 되는데, 그러면 화면에 단계는 보이는데
     * 시각 자리가 전부 비어서 "언제 일인지 모르는 목록"이 된다. 그래서 점·하이픈·
     * 슬래시를 모두 숫자로 보고 읽는다.
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

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

    /**
     * 인증키. 포털이 보여주는 두 형태 중 어느 쪽을 넣어도 받는다.
     *
     * <p>공공데이터포털은 같은 키를 Encoding({@code t%2FFP...%3D%3D})과
     * Decoding({@code t/FP...==}) 두 가지로 보여주고, <b>위에 있는 Encoding 쪽을
     * 복사하게 된다.</b> 그대로 넣으면 아래에서 한 번 더 인코딩되어 {@code %252F} 가
     * 되고, 우체국은 "키가 틀렸다"가 아니라 빈 결과를 돌려준다.
     *
     * <p>base64 알파벳에 {@code %} 가 없으므로, {@code %} 가 보이면 인코딩된 키다 —
     * 헷갈릴 여지가 없다. 그래서 설정에 주의를 적는 대신 여기서 받아 준다.
     */
    private final String serviceKey;

    public KoreaPostTrackingProvider(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.tracking();
        this.restClient = builder.build();
        this.serviceKey = decodeIfEncoded(config.serviceKey());

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

    /**
     * Encoding 키로 보이면 한 번 풀어 준다.
     *
     * <p>base64 알파벳({@code A-Z a-z 0-9 + / =})에 {@code %} 가 없으므로, {@code %} 가
     * 들어 있으면 퍼센트 인코딩된 값이다. 풀지 않고 넘기면 아래 URI 빌더가 {@code %} 를
     * 다시 인코딩해 {@code %252F} 가 되고, 우체국은 오류가 아니라 <b>빈 결과</b>를
     * 돌려줘서 왜 조회가 안 되는지 알 수 없게 된다.
     */
    private static String decodeIfEncoded(String raw) {
        if (raw == null || raw.indexOf('%') < 0) {
            return raw;
        }
        try {
            String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            log.info("[TRACKING] 인증키가 Encoding 형태라 디코딩해서 쓴다 "
                    + "(포털의 Decoding 키를 넣으면 이 줄이 사라진다)");
            return decoded;
        } catch (IllegalArgumentException e) {
            // 퍼센트가 있는데 디코딩이 안 된다 — 키가 잘린 것일 수 있다.
            // 그대로 써 보고 우체국이 뭐라 하는지 로그에 남게 둔다.
            log.warn("[TRACKING] 인증키를 디코딩하지 못했다. 값이 잘렸는지 확인이 필요하다");
            return raw;
        }
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

        Parsed parsed = parse(fetch(trackingNo));

        if (parsed.steps().isEmpty()) {
            // 단계가 하나도 없는 것은 "아직 집화 전"일 수도, 번호가 틀린 것일 수도 있다.
            // 둘을 구분할 방법이 없어 실패로 두지 않는다 — 실패로 두면 접수 직후
            // 몇 시간 동안 매 주기마다 오류 로그가 쌓인다.
            log.debug("[TRACKING] 진행 단계 없음 trackingNo={}", trackingNo);
            return new TrackingResult(null, false, false, List.of());
        }

        /*
         * 전체 상태는 최상위 dlvySttus 를 먼저 쓴다 (규격서 「배달상태」).
         *
         * 마지막 단계의 processSttus 로 유추하지 않는다 — 재배달·반송 흐름에서
         * 마지막 줄이 "배달완료"가 아닌데도 실제로는 끝났거나 그 반대인 경우가 있고,
         * 어느 쪽이 맞는지는 우체국이 정한다.
         */
        TrackingResult.Step last = parsed.steps().get(parsed.steps().size() - 1);
        String status = parsed.overallStatus() != null ? parsed.overallStatus() : last.what();
        boolean delivered = status != null && status.contains(DELIVERED_MARK);

        return new TrackingResult(status, !delivered, delivered, parsed.steps());
    }

    /** 응답에서 읽어낸 것. 전체 상태는 단계와 별도로 최상위에 온다. */
    private record Parsed(String overallStatus, List<TrackingResult.Step> steps) {}

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
        /*
         * ⚠️ URI 객체로 넘긴다. 문자열로 넘기면 안 된다.
         *
         * RestClient.uri(String) 은 받은 문자열을 <b>URI 템플릿으로 보고 한 번 더
         * 인코딩한다.</b> 그러면 여기서 인코딩한 "%3D" 의 % 가 다시 "%25" 가 되어
         * "%253D" 로 나가고, 우체국은 "키가 틀렸다"가 아니라 빈 결과를 돌려준다 —
         * 조회가 안 되는데 오류도 안 나는, 가장 찾기 어려운 고장이 된다.
         *
         * uri(URI) 오버로드는 받은 값을 그대로 쓴다.
         */
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("serviceKey", serviceKey)
                .queryParam("rgist", trackingNo)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

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
    private Parsed parse(byte[] xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // XXE 차단. 외부 응답을 파싱하는 곳이라 반드시 끈다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            // 바이트로 넘긴다 — 파서가 XML 선언부에서 인코딩을 읽는다 (없으면 UTF-8).
            var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

            /*
             * 오류 봉투를 먼저 본다.
             *
             * 우체국은 실패를 HTTP 상태가 아니라 본문으로 알린다 — 키가 틀려도,
             * 한도를 넘겨도 200 이 온다. 이것을 보지 않으면 단계 0개가 돌아오고,
             * 그것은 "아직 집화 전"과 똑같이 생겼다. 그러면 조회가 몇 달째 아무것도
             * 못 가져오는데 아무도 모르는 상태가 된다.
             */
            requireSuccess(doc.getDocumentElement());

            return new Parsed(
                    text(doc.getDocumentElement(), "dlvySttus"),
                    stepsOf(doc));
        } catch (DeliveryTrackingException e) {
            throw e;
        } catch (Exception e) {
            throw new DeliveryTrackingException("우체국 응답을 읽지 못했다: " + e.getMessage(), e);
        }
    }

    /**
     * 진행 단계를 모은다.
     *
     * <p>규격서가 정한 행 이름은 {@code longitudinalDomesticList} 다. 그것으로 먼저
     * 찾고, 없으면 {@code dlvyDate} 를 가진 노드의 부모로 되짚는다.
     *
     * <p>⚠️ 행을 {@code detailDc} 로 찾지 않는다. 그 항목은 <b>필수가 아니고</b>
     * 실제 응답에서 대부분 {@code <detailDc/>} 로 비어 있다 — 아예 빠진 행이 오면
     * 그 단계를 통째로 놓친다. 날짜는 모든 행에 있다(항목구분 1).
     */
    private static List<TrackingResult.Step> stepsOf(org.w3c.dom.Document doc) {
        List<TrackingResult.Step> steps = new ArrayList<>();

        NodeList rows = doc.getElementsByTagName("longitudinalDomesticList");
        if (rows.getLength() > 0) {
            for (int i = 0; i < rows.getLength(); i++) {
                if (rows.item(i) instanceof Element row) {
                    steps.add(stepOf(row));
                }
            }
            return steps;
        }

        // 래퍼 이름이 바뀐 경우. 날짜를 가진 노드의 부모가 한 행이다.
        NodeList dates = doc.getElementsByTagName("dlvyDate");
        for (int i = 0; i < dates.getLength(); i++) {
            if (dates.item(i).getParentNode() instanceof Element row) {
                steps.add(stepOf(row));
            }
        }
        return steps;
    }

    private static TrackingResult.Step stepOf(Element row) {
        String what = text(row, "processSttus");
        String detail = text(row, "detailDc");

        /*
         * 상세설명을 버리지 않는다.
         *
         * 마지막 단계의 detailDc 에 "배달(수령인: 홍*동 - 회사동료)" 처럼 누가 받았는지가
         * 들어온다. 고가 귀금속에서 그 한 줄이 분실·수령거부를 다툴 때 근거가 된다.
         * 수령인 이름은 우체국이 이미 가려서 준다.
         */
        if (what == null || what.isBlank()) {
            what = detail;
        } else if (detail != null && !detail.isBlank()) {
            what = what + " · " + detail;
        }

        return new TrackingResult.Step(
                at(text(row, "dlvyDate"), text(row, "dlvyTime")),
                text(row, "nowLc"),
                what);
    }

    /**
     * {@code <cmmMsgHeader>} 를 보고 실패면 던진다.
     *
     * <p>「조회결과가 없습니다」(03)만 예외로 통과시킨다 — 아직 집화 전인 정상 상태와
     * 구분할 수 없기 때문이다. 나머지(키 오류·한도 초과·서비스 중지)는 사람이 손을
     * 써야 하는 일이라 로그에 남아야 한다.
     */
    private static void requireSuccess(Element root) {
        String successYn = text(root, "successYN");
        if (successYn == null || !"N".equalsIgnoreCase(successYn)) {
            return;   // 성공이거나, 봉투가 없는 응답이다
        }

        String code = text(root, "returnCode");
        if (NO_RESULT_CODE.equals(code)) {
            return;   // 아직 아무것도 안 찍혔다. 실패가 아니다
        }

        // errMsg 를 그대로 싣는다. "SERVICE_KEY_IS_NOT_REGISTERED" 같은 말이 와야
        // 무엇을 고쳐야 하는지 알 수 있다. 키 자체는 들어 있지 않다.
        throw new DeliveryTrackingException(
                "우체국이 거절했다 code=" + code + " msg=" + text(root, "errMsg"));
    }

    /** 구분자를 걷어내고 숫자만. 규격서와 샘플이 서로 다른 구분자를 쓴다. */
    private static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
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
            // 구분자를 걷어내고 숫자만 본다 — "2017-09-12" 와 "2017.09.12" 를 함께 받는다.
            LocalDate d = LocalDate.parse(digits(date), DATE);
            LocalTime t = time == null || digits(time).isEmpty()
                    ? LocalTime.MIDNIGHT
                    : LocalTime.parse(digits(time), TIME);
            return LocalDateTime.of(d, t).atZone(KST).toOffsetDateTime();
        } catch (Exception e) {
            log.debug("[TRACKING] 시각을 읽지 못했다 date={} time={}", date, time);
            return null;
        }
    }
}
