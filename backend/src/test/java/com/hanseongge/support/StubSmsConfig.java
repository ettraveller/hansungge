package com.hanseongge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 문자 대역을 등록한다.
 *
 * <p>운영용 {@code NcpSensSmsSender} 는 {@code @Profile("!local & !dev")} 라 테스트 프로파일에서
 * 실제로 뜬다. 설정이 비어 있어 "문자 없음"으로 보이므로, 대역으로 덮어써야 인증번호 로그인을
 * 검증할 수 있다 ({@code StubMailConfig} 와 같다).
 */
@TestConfiguration
public class StubSmsConfig {

    @Bean
    public StubSmsSender ncpSensSmsSender() {
        return new StubSmsSender();
    }
}
