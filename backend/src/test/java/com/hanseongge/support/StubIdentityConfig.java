package com.hanseongge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 본인확인 대역을 등록한다.
 *
 * <p>운영용 {@code InicisIdentityVerificationProvider} 는 기본 비활성이고, 개발용
 * {@code MockIdentityVerificationProvider} 는 {@code local}/{@code dev} 프로파일 전용이라
 * 테스트에서는 <b>공급자가 하나도 없다.</b> 그 상태로는 가입 흐름을 한 줄도 검증할 수 없다.
 *
 * <p>{@code StubOAuthConfig} 와 달리 실물을 덮어쓰지 않고 <b>새로 더한다.</b> 그래서
 * {@code allow-bean-definition-overriding} 이 필요 없다.
 */
@TestConfiguration
public class StubIdentityConfig {

    @Bean
    public StubIdentityVerificationProvider stubIdentityVerificationProvider() {
        return new StubIdentityVerificationProvider();
    }
}
