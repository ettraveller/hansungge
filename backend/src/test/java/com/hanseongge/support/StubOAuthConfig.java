package com.hanseongge.support;

import com.hanseongge.domain.user.entity.OAuthProviderType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 실제 제공자 빈을 테스트 대역으로 갈아 끼운다.
 *
 * <p>빈 이름을 {@code googleOAuthProvider} 로 맞춰 컴포넌트 스캔이 등록한 실물 위에
 * 덮어쓴다. 그래서 이 설정을 쓰는 테스트는
 * {@code spring.main.allow-bean-definition-overriding=true} 가 필요하다.
 *
 * <p>네이버·카카오는 실물 그대로 둔다 — 클라이언트 설정이 없어 "사용 불가"로 나오며,
 * 그 상태 자체가 검증 대상이다 (앱 검수 전 제공자가 화면에 뜨면 안 된다).
 */
@TestConfiguration
public class StubOAuthConfig {

    @Bean
    public StubOAuthProvider googleOAuthProvider() {
        return new StubOAuthProvider(OAuthProviderType.GOOGLE);
    }
}
