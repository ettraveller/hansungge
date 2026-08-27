package com.hanseongge.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 실제 PostgreSQL 16 을 띄우는 통합테스트 기반 클래스.
 *
 * <p><b>왜 임베디드 PostgreSQL 인가:</b>
 * <ul>
 *   <li>H2 로 대체할 수 없다 — V1~V5 가 JSONB, plpgsql 트리거, 부분 UNIQUE 인덱스,
 *       {@code unnest()} 를 쓴다. 이것들이 실제로 도는지가 검증의 핵심이다.</li>
 *   <li>Testcontainers 를 쓸 수 없다 — 프로젝트 결정상 Docker 를 사용하지 않는다.</li>
 * </ul>
 * zonky embedded-postgres 는 진짜 PostgreSQL 바이너리를 내려받아 별도 프로세스로
 * 구동하므로 Docker 없이 동일한 검증이 가능하다.
 *
 * <p>인스턴스는 JVM 당 하나만 만들고 재사용한다. 테스트 클래스마다 PostgreSQL 을
 * 새로 띄우면 수십 초가 낭비된다.
 */
public abstract class EmbeddedPostgresSupport {

    private static volatile EmbeddedPostgres instance;

    static synchronized EmbeddedPostgres postgres() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder().start();
                EmbeddedPostgres started = instance;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        started.close();
                    } catch (IOException ignored) {
                        // JVM 종료 중이므로 더 할 수 있는 일이 없다
                    }
                }));
            } catch (IOException e) {
                throw new UncheckedIOException("임베디드 PostgreSQL 기동 실패", e);
            }
        }
        return instance;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        EmbeddedPostgres pg = postgres();
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
