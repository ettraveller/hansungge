package com.hanseongge;

import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.Migrations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 서블릿 컨테이너를 띄우고 HTTP 로 헬스체크를 호출한다.
 *
 * <p>MockMvc 는 서블릿 컨테이너 없이 디스패처만 태우므로 "정말 기동되는가"를 증명하지 못한다.
 * 이 테스트는 Tomcat + 실제 PostgreSQL + Flyway 를 모두 태운 뒤 소켓으로 요청한다 —
 * STEP 3 완료 조건인 "기동 + Flyway 적용 + 헬스체크"를 실제 경로로 확인하는 지점이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthHttpIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Tomcat 기동 후 HTTP GET /api/v1/health 가 200 과 기동 상태를 돌려준다")
    void healthOverHttp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"service\":\"hanseongge-api\"")
                .contains("\"database\":\"UP\"")
                .contains("\"schemaVersion\":\"" + Migrations.latest() + "\"")
                .contains("\"roundingPolicy\":\"CEIL_100\"");
    }

    @Test
    @DisplayName("운영 문서 경로는 test 프로파일에서 노출되지 않는다 — SwaggerConfig 는 local/dev 전용")
    void swaggerIsNotExposedOutsideLocalAndDev() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }
}
