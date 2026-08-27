package com.hanseongge.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 관리자 권한 계층 + 메서드 보안 (설계서 §7.4).
 *
 * <pre>
 * SUPER_ADMIN → ADMIN → MANAGER → OPERATOR
 * </pre>
 *
 * <p><b>계층을 두는 이유:</b> 계층이 없으면 {@code @PreAuthorize} 마다
 * {@code hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')} 처럼 나열해야 한다. 등급이 하나
 * 늘어나는 날 모든 어노테이션을 찾아 고쳐야 하고, 한 곳만 빠뜨리면 권한 구멍이 된다.
 * 상하관계를 여기 한 번만 선언하면 각 엔드포인트는 <b>최소 등급</b>만 말하면 된다.
 *
 * <p>주의: 계층은 "위 등급이 아래 등급의 권한을 <b>포함</b>한다"는 뜻이다. 특정 등급
 * <b>에게만</b> 열어야 하는 기능은 계층으로 표현되지 않으므로 서비스 계층에서 따로 막는다
 * (예: 마지막 SUPER_ADMIN 보호 규칙).
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    /**
     * static 인 이유: {@link EnableMethodSecurity} 가 만드는 인프라 빈보다 먼저 만들어져야
     * 한다. 인스턴스 메서드로 두면 설정 클래스 전체가 조기 초기화되어 다른 빈의 프록시가
     * 적용되지 않는 사고가 난다.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMIN").implies("ADMIN")
                .role("ADMIN").implies("MANAGER")
                .role("MANAGER").implies("OPERATOR")
                .build();
    }
}
