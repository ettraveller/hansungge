package com.hanseongge.global.health;

import com.hanseongge.global.util.PriceRoundingResolver;
import com.hanseongge.global.util.VatPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 헬스체크 서비스.
 *
 * <p>단순히 "떠 있음"만 보고하지 않고, <b>이 애플리케이션이 실제로 일할 수 있는 상태인지</b>를
 * 확인한다 — DB 연결, Flyway 스키마 적용 여부, 그리고 미확정 정책(A-1 부가세 · A-2 반올림) 적용 상태.
 * 배포 직후 사람이 가장 먼저 확인해야 하는 정보를 한 번에 보여주기 위함이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final PriceRoundingResolver priceRoundingResolver;
    private final VatPolicyResolver vatPolicyResolver;

    @Value("${spring.application.name}")
    private String serviceName;

    public HealthResponse getHealth() {
        String database = "UP";
        String schemaVersion = "UNKNOWN";
        try {
            schemaVersion = jdbcTemplate.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success = true "
                            + "ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
        } catch (Exception e) {
            database = "DOWN";
            log.warn("[HEALTH] DB 또는 Flyway 이력 조회 실패: {}", e.getMessage());
        }

        return new HealthResponse(
                serviceName,
                resolveVersion(),
                resolveProfile(),
                database,
                schemaVersion,
                priceRoundingResolver.resolve().name(),
                priceRoundingResolver.isUsingTemporaryDefault(),
                vatPolicyResolver.resolve().name(),
                vatPolicyResolver.isUsingTemporaryDefault(),
                OffsetDateTime.now(KST));
    }

    private String resolveProfile() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0
                ? String.join(",", environment.getDefaultProfiles())
                : String.join(",", active);
    }

    private String resolveVersion() {
        String implementationVersion = HealthService.class.getPackage().getImplementationVersion();
        return implementationVersion != null ? implementationVersion : "0.1.0";
    }
}
