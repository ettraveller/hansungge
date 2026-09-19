package com.hanseongge.domain.notification;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * NCP SENS 문자 발송.
 *
 * <h2>왜 이 공급자인가</h2>
 * 서버·DB·메일이 이미 NCP 다. 계정과 청구가 한곳에 모이고 추가 계약이 없다.
 *
 * <h2>⚠️ 발신번호를 먼저 등록해야 한다</h2>
 * 전기통신사업법상 발신번호는 사전 등록제다. NCP 콘솔에서 번호 소유를 증명하고 승인받은
 * 번호만 {@code SENS_SENDER_PHONE} 에 넣을 수 있다. 등록되지 않은 번호로 보내면 공급자가
 * 거부하고, 사용자는 오지 않는 문자를 기다린다.
 *
 * <h2>운영 전용이다</h2>
 * 로컬·개발은 {@link LoggingSmsSender} 가 콘솔에 찍는다 — {@link NcpOutboundMailSender} 와 같다.
 */
@Slf4j
@Component
@Profile("!local & !dev")
public class NcpSensSmsSender implements SmsSender {

    private static final String BASE_URL = "https://sens.apigw.ntruss.com";

    private final AppProperties.Sms config;
    private final RestClient restClient;

    public NcpSensSmsSender(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.sms();

        // 타임아웃을 반드시 준다. 기본값은 사실상 무한 대기라, 문자 서버가 응답하지 않으면
        // 로그인 요청 스레드가 그대로 묶인다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()))
                .withReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds()));

        this.restClient = builder
                .baseUrl(BASE_URL)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        if (!config.isConfigured()) {
            log.warn("""
                    [SMS] NCP SENS 설정이 비어 있습니다. 인증번호 로그인이 꺼집니다.
                    SENS_ACCESS_KEY / SENS_SECRET_KEY / SENS_SERVICE_ID / SENS_SENDER_PHONE 을 주입하세요.
                    발신번호는 NCP 콘솔에서 사전 등록(승인)된 번호여야 합니다.""");
        }
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public void send(String phone, String text) {
        if (!config.isConfigured()) {
            throw new SmsSendException("문자 발송 설정이 비어 있습니다");
        }

        String path = "/sms/v2/services/" + config.serviceId() + "/messages";
        String timestamp = String.valueOf(System.currentTimeMillis());

        try {
            restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", config.accessKey())
                    .header("x-ncp-apigw-signature-v2", NcpApiSigner.sign(
                            "POST", path, timestamp, config.accessKey(), config.secretKey()))
                    .body(Map.of(
                            "type", "SMS",
                            "contentType", "COMM",
                            "countryCode", "82",
                            "from", config.senderPhone(),
                            "content", text,
                            "messages", List.of(Map.of("to", phone))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 응답 원문을 예외에 담지 않는다 — 계정 정보가 섞여 나온다. 로그에만 남긴다.
            log.warn("[SMS] 발송 실패. to={}", mask(phone), e);
            throw new SmsSendException("문자 발송 실패", e);
        }
    }

    /** 로그에 번호 전체를 남기지 않는다. */
    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "***" + phone.substring(phone.length() - 4);
    }
}
