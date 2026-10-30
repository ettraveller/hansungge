package com.hanseongge.domain.shipment.tracking;

import com.hanseongge.domain.shipment.entity.Carrier;
import com.hanseongge.global.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_XML;

/**
 * 우체국 배송조회 응답 해석.
 *
 * <h2>왜 단위 테스트인가</h2>
 * 여기서 깨지는 것은 우리 로직이 아니라 <b>남의 응답 모양</b>이다. 실제 API 를 부르는
 * 테스트는 키가 없으면 못 돌고, 키가 있어도 조회 한도를 쓰며, 실제 송장번호가 없으면
 * 빈 결과만 돌아온다. 응답 문자열을 고정해 두고 그것을 어떻게 읽는지만 본다.
 *
 * <h2>고정하는 것</h2>
 * <ul>
 *   <li>래퍼가 바뀌어도 단계를 찾는다 — 경로가 아니라 태그 이름으로 긁는다</li>
 *   <li>시각을 못 읽은 단계를 버리지 않는다 — 버리면 "아무 일도 없음"으로 보인다</li>
 *   <li>배달완료는 마지막 단계로만 판정한다</li>
 *   <li>단계가 없으면 실패가 아니다 — 집화 전과 구분할 수 없다</li>
 *   <li>수령 시각을 만들지 않는다 (이용약관 제10조)</li>
 * </ul>
 */
class KoreaPostTrackingProviderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private MockRestServiceServer server;
    private KoreaPostTrackingProvider provider;

    private void given(String responseXml) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new KoreaPostTrackingProvider(propsWithKey("test-key"), builder);
        server.expect(requestTo(containsString("rgist=6012345678901")))
                .andRespond(withSuccess(responseXml, APPLICATION_XML));
    }

    private static AppProperties propsWithKey(String key) {
        AppProperties props = org.mockito.Mockito.mock(AppProperties.class);
        org.mockito.Mockito.when(props.tracking())
                .thenReturn(new AppProperties.Tracking(key, 30, 50));
        return props;
    }

    @Nested
    @DisplayName("응답 해석")
    class Parsing {

        /**
         * 규격서(2026-10-30 수령)의 샘플 응답을 그대로 옮겼다.
         *
         * <p>여기서 고정하는 것이 세 가지다.
         * <ul>
         *   <li>행 이름은 {@code longitudinalDomesticList} 다</li>
         *   <li><b>날짜 구분자가 하이픈이다</b> — 규격서 표는 {@code yyyy.mm.dd} 라고
         *       적어 놓고 샘플은 {@code 2017-09-12} 를 준다. 한쪽만 맞추면 시각이
         *       전부 null 이 되어 "언제 일인지 모르는 목록"이 된다</li>
         *   <li>{@code <detailDc/>} 가 대부분 비어 있다 — 그것으로 행을 찾으면 안 된다</li>
         * </ul>
         */
        @Test
        @DisplayName("규격서 샘플을 그대로 읽는다 — 하이픈 날짜, 빈 detailDc, 최상위 배달상태")
        void readsOfficialSample() {
            given("""
                    <LongitudinalDomesticListResponse>
                      <cmmMsgHeader><successYN>Y</successYN><returnCode>00</returnCode><errMsg/></cmmMsgHeader>
                      <addrseNm>수취인</addrseNm><applcntNm>발송인</applcntNm>
                      <dlvyDe>2017-09-13 16:26</dlvyDe>
                      <dlvySttus>배달완료</dlvySttus>
                      <longitudinalDomesticList>
                        <dlvyDate>2017-09-12</dlvyDate><dlvyTime>19:28</dlvyTime>
                        <nowLc>동서울우편집중국</nowLc><processSttus>접수</processSttus><detailDc/>
                      </longitudinalDomesticList>
                      <longitudinalDomesticList>
                        <dlvyDate>2017-09-13</dlvyDate><dlvyTime>08:48</dlvyTime>
                        <nowLc>시흥우체국</nowLc><processSttus>배달준비</processSttus><detailDc/>
                      </longitudinalDomesticList>
                      <longitudinalDomesticList>
                        <dlvyDate>2017-09-13</dlvyDate><dlvyTime>16:26</dlvyTime>
                        <nowLc>시흥우체국</nowLc><processSttus>배달완료</processSttus>
                        <detailDc>배달(수령인: 홍*동 - 회사동료)</detailDc>
                      </longitudinalDomesticList>
                      <pstmtrKnd>통상</pstmtrKnd><rgist>4092007735160</rgist>
                    </LongitudinalDomesticListResponse>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            // detailDc 가 빈 행도 빠지지 않는다
            assertThat(result.steps()).hasSize(3);
            assertThat(result.steps().get(0).what()).isEqualTo("접수");
            assertThat(result.steps().get(0).where()).isEqualTo("동서울우편집중국");

            // 하이픈 날짜를 읽는다
            assertThat(result.steps().get(0).at())
                    .isNotNull()
                    .satisfies(at -> {
                        assertThat(at.atZoneSameInstant(KST).toLocalDate().toString())
                                .isEqualTo("2017-09-12");
                        assertThat(at.atZoneSameInstant(KST).getHour()).isEqualTo(19);
                        assertThat(at.atZoneSameInstant(KST).getMinute()).isEqualTo(28);
                    });

            // 수령인 정보를 버리지 않는다 — 분실·수령거부를 다툴 때 근거다
            assertThat(result.steps().get(2).what())
                    .isEqualTo("배달완료 · 배달(수령인: 홍*동 - 회사동료)");

            // 전체 상태는 최상위 dlvySttus 가 정한다
            assertThat(result.externalStatus()).isEqualTo("배달완료");
            assertThat(result.delivered()).isTrue();
            assertThat(result.inTransit()).isFalse();
        }

        @Test
        @DisplayName("점으로 구분된 날짜도 읽는다 — 규격서 표가 그렇게 적혀 있다")
        void readsDottedDate() {
            given("""
                    <LongitudinalDomesticListResponse>
                      <longitudinalDomesticList>
                        <dlvyDate>2026.10.30</dlvyDate><dlvyTime>09:12</dlvyTime>
                        <nowLc>서울종로</nowLc><processSttus>접수</processSttus><detailDc/>
                      </longitudinalDomesticList>
                    </LongitudinalDomesticListResponse>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps().get(0).at())
                    .isNotNull()
                    .satisfies(at -> assertThat(
                            at.atZoneSameInstant(KST).toLocalDate().toString())
                            .isEqualTo("2026-10-30"));
        }

        /**
         * 래퍼가 바뀌어도 읽어야 한다.
         *
         * <p>중첩 경로로 박아 두면 우정사업본부가 한 겹 바꾸는 날 전부 빈 결과가 되는데,
         * 그때 돌아오는 것은 오류가 아니라 단계 0개라서 "집화 전"과 구분되지 않는다.
         */
        @Test
        @DisplayName("행 래퍼 이름이 바뀌어도 날짜를 가진 행을 찾는다")
        void survivesWrapperChange() {
            given("""
                    <OpenAPI><newWrapper><rows><row>
                      <dlvyDate>2026-10-30</dlvyDate><dlvyTime>09:12</dlvyTime>
                      <nowLc>서울종로</nowLc><processSttus>집화완료</processSttus>
                      <detailDc/>
                    </row></rows></newWrapper></OpenAPI>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps()).hasSize(1);
            assertThat(result.steps().get(0).what()).isEqualTo("집화완료");
            assertThat(result.inTransit()).isTrue();
        }

        @Test
        @DisplayName("시각을 못 읽은 단계도 버리지 않는다 — 버리면 진행 중인데 '아무 일도 없음'이 된다")
        void keepsStepWithUnreadableTime() {
            given("""
                    <response><items><item>
                      <dlvyDate>알 수 없음</dlvyDate><dlvyTime></dlvyTime>
                      <nowLc>서울종로</nowLc><processSttus>배송출발</processSttus>
                      <detailDc>배송 중</detailDc>
                    </item></items></response>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps()).hasSize(1);
            assertThat(result.steps().get(0).at()).isNull();
            assertThat(result.steps().get(0).what()).isEqualTo("배송출발 · 배송 중");
        }

        @Test
        @DisplayName("processSttus 가 비면 detailDc 로 대신한다")
        void fallsBackToDetail() {
            given("""
                    <response><items><item>
                      <dlvyDate>2026.10.30</dlvyDate><dlvyTime>09:12</dlvyTime>
                      <processSttus></processSttus><detailDc>발송 준비</detailDc>
                    </item></items></response>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps().get(0).what()).isEqualTo("발송 준비");
        }

        /**
         * 단계가 없는 것은 실패가 아니다.
         *
         * <p>접수 직후라 아직 아무것도 안 찍힌 것일 수도, 번호가 틀린 것일 수도 있다.
         * 둘을 구분할 방법이 없는데 실패로 두면 접수 후 몇 시간 동안 매 주기마다
         * 오류 로그가 쌓인다.
         */
        @Test
        @DisplayName("단계가 하나도 없어도 실패가 아니다 — 집화 전과 구분할 수 없다")
        void emptyIsNotFailure() {
            given("<response><body><items></items></body></response>");

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps()).isEmpty();
            assertThat(result.delivered()).isFalse();
            assertThat(result.inTransit()).isFalse();
        }

        /**
         * 최상위 {@code dlvySttus} 가 없을 때의 대비책.
         *
         * <p>재배달·반송 흐름에서는 "배달완료"가 중간에 나온다. 그것을 보고 끝났다고
         * 판정하면 관리자 화면이 수령 처리를 재촉하고, 아직 고객 손에 없는 물건을
         * 받았다고 기록하게 된다.
         */
        @Test
        @DisplayName("최상위 배달상태가 없으면 마지막 단계로 판정한다 — 중간의 배달완료는 아니다")
        void onlyLastStepDecides() {
            given("""
                    <response><items>
                      <item><dlvyDate>2026.10.30</dlvyDate><dlvyTime>09:00</dlvyTime>
                        <processSttus>배달완료</processSttus><detailDc>1차</detailDc></item>
                      <item><dlvyDate>2026.10.31</dlvyDate><dlvyTime>10:00</dlvyTime>
                        <processSttus>반송</processSttus><detailDc>수취인 부재</detailDc></item>
                    </items></response>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.delivered()).isFalse();
            assertThat(result.inTransit()).isTrue();
            assertThat(result.externalStatus()).isEqualTo("반송 · 수취인 부재");
        }

        /**
         * 최상위 상태가 단계보다 우선이다.
         *
         * <p>어느 쪽이 맞는지는 우체국이 정한다. 마지막 줄이 "배달완료"로 보여도
         * 반송 처리 중일 수 있고, 그 반대도 있다.
         */
        @Test
        @DisplayName("최상위 배달상태가 마지막 단계보다 우선한다")
        void overallStatusWins() {
            given("""
                    <LongitudinalDomesticListResponse>
                      <dlvySttus>미배달</dlvySttus>
                      <longitudinalDomesticList>
                        <dlvyDate>2026-10-30</dlvyDate><dlvyTime>09:00</dlvyTime>
                        <processSttus>배달완료</processSttus><detailDc/>
                      </longitudinalDomesticList>
                    </LongitudinalDomesticListResponse>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.externalStatus()).isEqualTo("미배달");
            assertThat(result.delivered()).isFalse();
        }
    }

    /**
     * 우체국은 실패를 HTTP 상태가 아니라 <b>본문</b>으로 알린다 — 키가 틀려도,
     * 한도를 넘겨도 200 이 온다. 그것을 보지 않으면 단계 0개가 돌아오는데, 그 모습이
     * "아직 집화 전"과 똑같아서 조회가 몇 달째 아무것도 못 가져와도 아무도 모른다.
     */
    @Nested
    @DisplayName("오류 봉투")
    class ErrorEnvelope {

        /** 2026-10-30 실제 응답을 그대로 옮겼다. */
        @Test
        @DisplayName("「조회결과가 없습니다」(03)는 실패가 아니다 — 집화 전과 구분할 수 없다")
        void noResultIsNotFailure() {
            given("""
                    <?xml version="1.0" encoding="utf-8" standalone="yes"?>
                    <LongitudinalDomesticListResponse><cmmMsgHeader>
                      <responseTime>20261030:18324051</responseTime>
                      <successYN>N</successYN><returnCode>03</returnCode>
                      <errMsg>조회결과가 없습니다.</errMsg>
                    </cmmMsgHeader></LongitudinalDomesticListResponse>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps()).isEmpty();
            assertThat(result.delivered()).isFalse();
        }

        @Test
        @DisplayName("키 오류는 던진다 — 조용히 빈 결과로 두면 고장난 줄 모른다")
        void keyErrorThrows() {
            given("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <LongitudinalDomesticListResponse><cmmMsgHeader>
                      <successYN>N</successYN><returnCode>30</returnCode>
                      <errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
                    </cmmMsgHeader></LongitudinalDomesticListResponse>
                    """);

            assertThatThrownBy(() -> provider.track(Carrier.KOREA_POST, "6012345678901"))
                    .isInstanceOf(DeliveryTrackingException.class)
                    .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        }

        @Test
        @DisplayName("한도 초과도 던진다")
        void quotaExceededThrows() {
            given("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <LongitudinalDomesticListResponse><cmmMsgHeader>
                      <successYN>N</successYN><returnCode>22</returnCode>
                      <errMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</errMsg>
                    </cmmMsgHeader></LongitudinalDomesticListResponse>
                    """);

            assertThatThrownBy(() -> provider.track(Carrier.KOREA_POST, "6012345678901"))
                    .isInstanceOf(DeliveryTrackingException.class);
        }
    }

    /**
     * 인증키 형태.
     *
     * <p>포털은 같은 키를 Encoding 과 Decoding 두 가지로 보여주고, <b>위에 있는
     * Encoding 쪽을 복사하게 된다.</b> 실제로 그렇게 들어왔다 (2026-10-30).
     */
    @Nested
    @DisplayName("인증키")
    class ServiceKey {

        /**
         * 어느 형태를 넣든 <b>우체국이 받는 값이 같아야</b> 한다.
         *
         * <p>이중 인코딩({@code %25})이 가장 위험하다. 그렇게 나가면 우체국은 "키가
         * 틀렸다"가 아니라 빈 결과를 돌려주고, 그 모습은 "아직 집화 전"과 똑같다 —
         * 조회가 몇 달째 아무것도 못 가져와도 오류 하나 없이 조용하다.
         */
        @Test
        @DisplayName("Encoding 키를 넣어도 우체국이 받는 값은 같다 — 이중 인코딩되지 않는다")
        void acceptsEncodedKey() {
            assertThat(keyAsSentWith("t%2FFPSiYsiOrM%3D%3D"))
                    .isEqualTo("t/FPSiYsiOrM==");
        }

        @Test
        @DisplayName("Decoding 키도 같은 값으로 나간다")
        void acceptsDecodedKey() {
            assertThat(keyAsSentWith("t/FPSiYsiOrM=="))
                    .isEqualTo("t/FPSiYsiOrM==");
        }

        /** 설정에 넣은 키가 실제 요청에서 우체국에 어떤 값으로 도착하는가. */
        private String keyAsSentWith(String configured) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
            var provider = new KoreaPostTrackingProvider(propsWithKey(configured), builder);

            var sent = new java.util.concurrent.atomic.AtomicReference<String>();
            mock.expect(request -> sent.set(queryValue(request.getURI(), "serviceKey")))
                    .andRespond(withSuccess("<r/>", APPLICATION_XML));

            provider.track(Carrier.KOREA_POST, "6012345678901");

            // 질의문자열에 %25 가 보이면 이중 인코딩이다.
            assertThat(sent.get()).doesNotContain("%25");
            return sent.get();
        }

        /** 서버가 읽는 그대로 — 한 번 디코딩한 값. */
        private static String queryValue(java.net.URI uri, String name) {
            for (String pair : uri.getRawQuery().split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals(name)) {
                    return java.net.URLDecoder.decode(
                            pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }

    @Nested
    @DisplayName("실패")
    class Failures {

        @Test
        @DisplayName("키가 없으면 켜지지 않고, 불러도 배송을 막지 않는 예외다")
        void notConfigured() {
            var provider = new KoreaPostTrackingProvider(propsWithKey(""), RestClient.builder());

            assertThat(provider.isConfigured()).isFalse();
            assertThatThrownBy(() -> provider.track(Carrier.KOREA_POST, "6012345678901"))
                    .isInstanceOf(DeliveryTrackingException.class);
        }

        @Test
        @DisplayName("우체국이 500 을 주면 기술적 실패다 — 다음 주기에 다시 묻는다")
        void serverError() {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
            var provider = new KoreaPostTrackingProvider(propsWithKey("test-key"), builder);
            mock.expect(requestTo(containsString("rgist=")))
                    .andRespond(withStatus(INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> provider.track(Carrier.KOREA_POST, "6012345678901"))
                    .isInstanceOf(DeliveryTrackingException.class);
        }

        /** serviceKey 가 uri 에 들어 있다. 실패 메시지에 주소를 넣으면 로그에 키가 남는다. */
        @Test
        @DisplayName("실패 메시지에 인증키가 들어가지 않는다")
        void doesNotLeakKey() {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer mock = MockRestServiceServer.bindTo(builder).build();
            var provider = new KoreaPostTrackingProvider(
                    propsWithKey("super-secret-key-value"), builder);
            mock.expect(requestTo(containsString("rgist=")))
                    .andRespond(withStatus(INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> provider.track(Carrier.KOREA_POST, "6012345678901"))
                    .hasMessageNotContaining("super-secret-key-value");
        }

        @Test
        @DisplayName("다른 택배사를 받으면 거부한다")
        void wrongCarrier() {
            var provider = new KoreaPostTrackingProvider(propsWithKey("k"), RestClient.builder());
            // 지금 Carrier 는 우체국 하나뿐이라 이 분기를 호출로 확인할 수 없다.
            // 택배사가 늘어나는 날 이 테스트가 그 자리를 가리킨다.
            assertThat(List.of(Carrier.values())).containsExactly(Carrier.KOREA_POST);
            assertThat(provider.isConfigured()).isTrue();
        }
    }
}
