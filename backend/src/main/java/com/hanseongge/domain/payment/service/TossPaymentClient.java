package com.hanseongge.domain.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 승인 요청.
 *
 * <h2>승인은 반드시 서버가 한다</h2>
 * 결제창이 성공 주소로 돌려보내는 {@code paymentKey}·{@code amount} 는 <b>브라우저를 거쳐
 * 온 값</b>이라 그대로 믿을 수 없다. 서버가 토스에 다시 물어 승인해야 결제가 성립하고,
 * 그때 보내는 금액은 <b>우리 DB 의 주문 금액</b>이어야 한다 — 쿼리로 넘어온 금액을 그대로
 * 보내면 금액을 바꿔 부르는 요청이 그대로 통과한다.
 *
 * <h2>테스트 키</h2>
 * 계약 전에도 토스가 공개한 테스트 키(`test_ck_...` / `test_sk_...`)로 끝까지 돌려볼 수 있다.
 * 실제 카드번호를 넣어도 <b>가상 승인</b>이고 돈이 빠지지 않는다.
 * ⚠️ 클라이언트 키와 시크릿 키는 <b>한 세트</b>여야 한다. 섞으면 {@code INVALID_API_KEY} 다.
 */
@Slf4j
@Component
public class TossPaymentClient {

    public static final String PROVIDER = "TOSS";

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    private final AppProperties.Payment config;
    private final RestClient restClient;

    public TossPaymentClient(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.payment();

        // 타임아웃을 반드시 준다. 승인 요청이 무한정 매달리면 그 스레드가 묶이고,
        // 고객은 결제가 됐는지 안 됐는지 모르는 채 화면 앞에 남는다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()))
                .withReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds()));

        this.restClient = builder
                .baseUrl(BASE_URL)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        if (!config.isConfigured()) {
            log.warn("""
                    [PAY] 토스페이먼츠 키가 비어 있습니다. 결제를 진행할 수 없습니다.
                    PAYMENT_TOSS_CLIENT_KEY / PAYMENT_TOSS_SECRET_KEY 를 주입하세요.""");
        } else if (config.isTestKey()) {
            log.warn("[PAY] 토스페이먼츠 **테스트 키**로 동작합니다. 실제 결제는 이뤄지지 않습니다.");
        }
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String clientKey() {
        return config.clientKey();
    }

    public boolean isTestMode() {
        return config.isTestKey();
    }

    /**
     * 승인 요청.
     *
     * @param amount <b>우리 DB 의 주문 금액.</b> 브라우저가 보낸 금액이 아니다
     * @throws PaymentApprovalException 토스가 거절했을 때 (사유는 로그에만)
     */
    public JsonNode confirm(String paymentKey, String orderNo, BigDecimal amount) {
        try {
            return restClient.post()
                    .uri(CONFIRM_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    // 같은 주문에 대한 승인 요청이 두 번 가도 토스가 한 번만 처리하게 한다.
                    .header("Idempotency-Key", PROVIDER + ":CONFIRM:" + orderNo)
                    .body(Map.of(
                            "paymentKey", paymentKey,
                            "orderId", orderNo,
                            // 토스는 원 단위 정수를 받는다. 소수점이 붙으면 거절된다.
                            "amount", amount.setScale(0, java.math.RoundingMode.UNNECESSARY)
                                    .toBigIntegerExact()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // 응답 원문을 예외 메시지에 담지 않는다 — 가맹점 정보가 섞여 나온다. 로그에만 남긴다.
            log.warn("[PAY] 승인 거절 orderNo={} status={} body={}",
                    orderNo, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentApprovalException("토스 승인 거절", e);
        } catch (ArithmeticException e) {
            // 금액에 소수점이 남아 있다는 뜻이다. 원 단위로 떨어지지 않는 주문은 만들지 않는다.
            log.error("[PAY] 주문 금액이 원 단위가 아닙니다. orderNo={} amount={}", orderNo, amount);
            throw new PaymentApprovalException("주문 금액이 원 단위가 아님", e);
        } catch (RuntimeException e) {
            log.warn("[PAY] 승인 요청 실패 orderNo={}", orderNo, e);
            throw new PaymentApprovalException("토스 승인 요청 실패", e);
        }
    }

    /**
     * 거래 조회. <b>웹훅을 믿지 않기 위해</b> 쓴다.
     *
     * <p>토스 웹훅에는 서명이 없다 — 주소만 알면 누구나 "결제됐다"고 우리 서버에 알릴 수 있다.
     * 그래서 웹훅 본문을 그대로 쓰지 않고, 거기 담긴 paymentKey 로 <b>토스에 직접 되물어</b>
     * 그 응답만 쓴다. 본인확인·소셜 로그인에서 쓰는 것과 같은 원칙이다.
     *
     * @throws PaymentApprovalException 조회에 실패했을 때
     */
    public JsonNode lookup(String paymentKey) {
        try {
            return restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("[PAY] 거래 조회 실패 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentApprovalException("토스 거래 조회 실패", e);
        } catch (RuntimeException e) {
            log.warn("[PAY] 거래 조회 요청 실패", e);
            throw new PaymentApprovalException("토스 거래 조회 요청 실패", e);
        }
    }

    /** {@code Basic base64(secretKey + ":")} — 비밀번호 없이 콜론만 붙인다. */
    private String basicAuth() {
        String raw = config.secretKey() + ":";
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
