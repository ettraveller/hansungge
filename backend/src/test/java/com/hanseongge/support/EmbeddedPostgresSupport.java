package com.hanseongge.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

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

    /**
     * PostgreSQL 기동을 기다리는 시간. zonky 기본값은 10초다.
     *
     * <p>10초로는 부족한 장비가 있다. 백신·EDR 이 프로세스 실행마다 검사하는 사내 PC 에서는
     * {@code postgres.exe --version} 한 번에도 2~3초가 걸린다. 기동 과정은 initdb·pg_ctl·
     * 준비 확인으로 프로세스를 여러 번 띄우므로 10초를 쉽게 넘고, 그러면 통합테스트가
     * <b>전부</b> "Failed to load ApplicationContext" 로 무너진다 — 원인이 코드에 있는 것처럼
     * 보여서 찾는 데 시간이 걸린다.
     *
     * <p>넉넉히 기다리는 것은 손해가 없다. 정상 장비에서는 어차피 몇 초 만에 떠서 이 값에
     * 닿지 않고, 느린 장비에서만 기다린다.
     */
    private static final Duration STARTUP_WAIT = Duration.ofSeconds(60);

    private static volatile EmbeddedPostgres instance;

    static synchronized EmbeddedPostgres postgres() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder()
                        .setPGStartupWait(STARTUP_WAIT)
                        .start();
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
