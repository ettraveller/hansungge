package com.hanseongge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 메일 대역을 등록한다.
 *
 * <p>운영용 {@code NcpOutboundMailSender} 는 {@code @Profile("!local & !dev")} 라 테스트
 * 프로파일에서 <b>실제로 뜬다.</b> 설정이 비어 있어 호출하면 예외가 나므로, 대역으로
 * 덮어써야 가입 흐름을 검증할 수 있다.
 */
@TestConfiguration
public class StubMailConfig {

    @Bean
    public StubMailSender ncpOutboundMailSender() {
        return new StubMailSender();
    }
}
