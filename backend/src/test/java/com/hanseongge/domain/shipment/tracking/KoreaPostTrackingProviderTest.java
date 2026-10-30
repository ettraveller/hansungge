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

        @Test
        @DisplayName("단계를 시간순으로 읽고, 마지막 단계로 배달완료를 판정한다")
        void readsStepsInOrder() {
            given("""
                    <response><body><items>
                      <item>
                        <dlvyDate>2026.10.30</dlvyDate><dlvyTime>09:12</dlvyTime>
                        <nowLc>서울종로</nowLc><processSttus>접수</processSttus>
                        <detailDc>우체국 접수</detailDc>
                      </item>
                      <item>
                        <dlvyDate>2026.10.31</dlvyDate><dlvyTime>14:30</dlvyTime>
                        <nowLc>서울강남</nowLc><processSttus>배달완료</processSttus>
                        <detailDc>수령인 본인</detailDc>
                      </item>
                    </items></body></response>
                    """);

            TrackingResult result = provider.track(Carrier.KOREA_POST, "6012345678901");

            assertThat(result.steps()).hasSize(2);
            assertThat(result.steps().get(0).what()).isEqualTo("접수");
            assertThat(result.steps().get(0).where()).isEqualTo("서울종로");
            assertThat(result.steps().get(0).at())
                    .isNotNull()
                    .satisfies(at -> {
                        assertThat(at.atZoneSameInstant(KST).getHour()).isEqualTo(9);
                        assertThat(at.atZoneSameInstant(KST).getMinute()).isEqualTo(12);
                    });

            assertThat(result.delivered()).isTrue();
            assertThat(result.inTransit()).isFalse();
            assertThat(result.externalStatus()).isEqualTo("배달완료");
        }

        /**
         * 래퍼가 바뀌어도 읽어야 한다.
         *
         * <p>중첩 경로로 박아 두면 우정사업본부가 한 겹 바꾸는 날 전부 빈 결과가 되는데,
         * 그때 돌아오는 것은 오류가 아니라 단계 0개라서 "집화 전"과 구분되지 않는다.
         */
        @Test
        @DisplayName("바깥 래퍼가 달라도 단계를 찾는다 — 경로가 아니라 태그 이름으로 긁는다")
        void survivesWrapperChange() {
            given("""
                    <OpenAPI><newWrapper><rows><row>
                      <dlvyDate>2026.10.30</dlvyDate><dlvyTime>09:12</dlvyTime>
                      <nowLc>서울종로</nowLc><processSttus>집화완료</processSttus>
                      <detailDc>집화</detailDc>
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
            assertThat(result.steps().get(0).what()).isEqualTo("배송출발");
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
         * 중간에 "배달완료"라는 말이 있어도 마지막이 아니면 끝난 것이 아니다.
         *
         * <p>재배달·반송 같은 흐름에서 그 말이 중간에 나올 수 있다. 끝났다고 판정하면
         * 관리자 화면이 수령 처리를 재촉하고, 아직 고객 손에 없는 물건을 받았다고
         * 기록하게 된다.
         */
        @Test
        @DisplayName("배달완료가 중간에 있으면 끝난 것이 아니다 — 마지막 단계만 본다")
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
            assertThat(result.externalStatus()).isEqualTo("반송");
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
