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
 * NCP Cloud Outbound Mailer.
 *
 * <h2>왜 이 공급자인가</h2>
 * 서버·DB 가 이미 NCP 다. 계정과 청구가 한곳에 모이고, 월 1,000건까지 무료라 인증 메일
 * 용도로는 사실상 비용이 없다.
 *
 * <h2>운영 전용이다</h2>
 * 로컬·개발은 {@link LoggingMailSender} 가 콘솔에 찍는다. 두 빈이 동시에 뜨면 주입이
 * 모호해지므로 프로파일로 갈라 둔다.
 *
 * <h2>⚠️ SPF·DKIM·DMARC 를 설정해야 한다</h2>
 * 이것 없이 보내면 <b>대부분 스팸함으로 간다.</b> 그러면 "가입이 안 된다"는 문의가
 * 들어오는데, 서버 로그에는 발송 성공만 남아 원인을 찾기 어렵다. TODO E-5 참조.
 */
@Slf4j
@Component
@Profile("!local & !dev")
public class NcpOutboundMailSender implements MailSender {

    private static final String BASE_URL = "https://mail.apigw.ntruss.com";
    private static final String PATH = "/api/v1/mails";

    private final AppProperties.Mail config;
    private final RestClient restClient;

    public NcpOutboundMailSender(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.mail();

        // 타임아웃을 반드시 준다. 기본값은 사실상 무한 대기라, 메일 서버가 응답하지 않으면
        // 가입 요청 스레드가 그대로 묶인다 — oauthRestClient 와 같은 이유다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()))
                .withReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds()));

        this.restClient = builder
                .baseUrl(BASE_URL)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        if (!config.isConfigured()) {
            log.warn("""
                    [MAIL] NCP Cloud Outbound Mailer 설정이 비어 있습니다. 인증 메일이 발송되지 않습니다.
                    APP_MAIL_ACCESS_KEY / APP_MAIL_SECRET_KEY / APP_MAIL_FROM_ADDRESS 를 주입하세요.""");
        }
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!config.isConfigured()) {
            throw new MailSendException("메일 발송 설정이 비어 있습니다 (TODO E-5)");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());

        try {
            restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", config.accessKey())
                    .header("x-ncp-apigw-signature-v2", sign("POST", PATH, timestamp))
                    .body(Map.of(
                            "senderAddress", config.fromAddress(),
                            "senderName", config.fromName(),
                            "title", subject,
                            "body", toHtml(body),
                            // 받는 사람마다 따로 보낸다. false 면 수신자 목록이 서로에게 보인다.
                            "individual", true,
                            "recipients", List.of(Map.of("address", to, "type", "R"))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 응답 원문을 예외에 담지 않는다 — 계정 정보가 섞여 나온다. 로그에만 남긴다.
            log.warn("[MAIL] 발송 실패. to={}", mask(to), e);
            throw new MailSendException("메일 발송 실패", e);
        }
    }

    /**
     * 일반 텍스트 본문을 HTML 로 옮긴다.
     *
     * <p>NCP 메일은 본문을 HTML 로 보여 준다. 줄바꿈만 있는 텍스트를 그대로 보내면 메일함에서 모든 줄이
     * 한 줄로 붙는다. 꺾쇠·앰퍼샌드는 이스케이프한다 — 본문에 사용자 입력이 섞여도 태그가 되지 않게.
     */
    static String toHtml(String text) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
        return "<div style=\"font-family:sans-serif;font-size:14px;line-height:1.7;color:#222\">"
                + escaped.strip().replace("\r\n", "\n").replace("\n", "<br>\n")
                + "</div>";
    }

    private String sign(String method, String url, String timestamp) {
        return NcpApiSigner.sign(method, url, timestamp, config.accessKey(), config.secretKey());
    }

    /** 로그에 주소 전체를 남기지 않는다. */
    private static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
