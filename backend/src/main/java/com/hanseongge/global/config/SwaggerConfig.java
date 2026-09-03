package com.hanseongge.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Swagger / OpenAPI 문서 설정.
 *
 * <p><b>local · dev 프로파일에서만 활성화된다.</b> prod 에서는 이 설정 클래스 자체가
 * 로드되지 않고, 추가로 {@code application-prod.yml} 이 springdoc 을 끄며,
 * Nginx 가 해당 경로를 404 로 막는다 — 3중으로 차단한다 (설계서 §15).
 *
 * <p>사용자 API 와 관리자 API 를 그룹으로 나눠, 프론트엔드 개발자가 관리자 스펙을
 * 뒤적이지 않아도 되게 한다.
 */
@Configuration
@Profile({"local", "dev"})
public class SwaggerConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI hanseonggeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("한성금거래소 쇼핑몰 API")
                        .version("v1")
                        .description("""
                                금·은·귀금속 온라인 쇼핑몰 REST API.

                                주의: 가격은 항상 서버가 계산한 값이다. 클라이언트가 계산하거나
                                전송한 금액은 주문·결제 어디에서도 사용되지 않는다.
                                """))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("1-사용자")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("2-관리자")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
