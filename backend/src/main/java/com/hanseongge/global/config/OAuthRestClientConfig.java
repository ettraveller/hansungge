package com.hanseongge.global.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 소셜 인증 전용 HTTP 클라이언트.
 *
 * <p><b>타임아웃을 반드시 준다.</b> 기본 설정은 사실상 무한 대기라, 제공자 서버가 응답하지
 * 않으면 로그인 요청 스레드가 그대로 묶인다. 소셜 로그인 하나가 느려져 톰캣 스레드풀이
 * 고갈되면 시세 조회·상품 조회까지 함께 멈춘다 — 외부 의존이 내부 기능을 끌어내리는
 * 전형적인 경로다.
 *
 * <p>이름을 {@code oauthRestClient} 로 고정한 것은, 나중에 PG·Object Storage 용 클라이언트가
 * 생겼을 때 타임아웃 정책이 서로 섞이지 않게 하기 위해서다.
 */
@Configuration
public class OAuthRestClientConfig {

    @Bean
    public RestClient oauthRestClient(AppProperties appProperties, RestClient.Builder builder) {
        AppProperties.OAuth oauth = appProperties.oauth();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(oauth.connectTimeoutSeconds()))
                .withReadTimeout(Duration.ofSeconds(oauth.readTimeoutSeconds()));
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
