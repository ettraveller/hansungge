package com.hanseongge;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.util.PriceRoundingResolver;
import com.hanseongge.global.util.RoundingPolicy;
import com.hanseongge.global.util.VatMode;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STEP 3 완료 검증 — 실제 PostgreSQL 위에서 전체 기동을 확인한다.
 *
 * <p>검증 범위: Flyway V1~V7 적용 → 스키마 정합성(ddl-auto=validate) → 설정 캐시 →
 * 최초 관리자 생성 → 헬스체크 응답.
 *
 * <p>관리자 인증·권한·감사로그는 {@link AdminAuthIntegrationTest} 가 맡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.admin-init.id=inittest",
        "app.admin-init.password=Init!Passw0rd"
})
class ApplicationBootstrapIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired SystemSettingService systemSettingService;
    @Autowired PriceRoundingResolver priceRoundingResolver;
    @Autowired PasswordEncoder passwordEncoder;

    // ---------- Flyway ----------

    @Test
    @DisplayName("Flyway V1~V7 이 적용되고 33개 테이블이 생성된다")
    void flywayAppliesAllMigrations() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);
        // V6(관리자 인증)은 컬럼·인덱스만 추가한다. V7 은 market_price_fetch_logs 1개를 더한다.
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7");

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tableCount).isEqualTo(33);
    }

    @Test
    @DisplayName("append-only 보호 장치가 실제로 생성된다 — 시세 트리거와 현재값 부분 UNIQUE 인덱스")
    void appendOnlyGuardsExist() {
        Integer trigger = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_mpe_guard'", Integer.class);
        assertThat(trigger).isEqualTo(1);

        Integer partialIndex = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname = 'ux_mpe_current' AND indexdef LIKE '%effective_to IS NULL%'",
                Integer.class);
        assertThat(partialIndex).isEqualTo(1);
    }

    // ---------- 설정 ----------

    @Test
    @DisplayName("system_settings 시드값을 읽는다")
    void readsSeededSettings() {
        assertThat(systemSettingService.getInt("MAX_PRICE_CHANGE_PERCENT", -1)).isEqualTo(5);
        assertThat(systemSettingService.getInt("PRICE_STALE_MINUTES", -1)).isEqualTo(240);
    }

    @Test
    @DisplayName("미확정('TBD') 설정은 값이 없는 것으로 취급된다 — 예외를 던지지 않는다")
    void undecidedSettingIsTreatedAsAbsent() {
        assertThat(systemSettingService.find("PRICE_ROUNDING_MODE")).isEmpty();
        assertThat(systemSettingService.find("VAT_MODE")).isEmpty();
    }

    @Test
    @DisplayName("설정을 바꾸면 캐시가 무효화되어 새 값이 읽힌다")
    void updateEvictsCache() {
        try {
            assertThat(priceRoundingResolver.resolve()).isEqualTo(RoundingPolicy.TEMPORARY_DEFAULT);

            systemSettingService.update("PRICE_ROUNDING_MODE", "FLOOR_100", "test");

            assertThat(systemSettingService.find("PRICE_ROUNDING_MODE")).contains("FLOOR_100");
            assertThat(priceRoundingResolver.resolve()).isEqualTo(RoundingPolicy.FLOOR_100);
            assertThat(priceRoundingResolver.isUsingTemporaryDefault()).isFalse();
        } finally {
            // 다른 테스트에 영향을 주지 않도록 미확정 상태로 되돌린다
            systemSettingService.update("PRICE_ROUNDING_MODE", SystemSettingService.UNDECIDED, "test");
        }
    }

    // ---------- 최초 관리자 ----------

    @Test
    @DisplayName("admin_users 가 비어 있으면 SUPER_ADMIN 1개가 BCrypt 해시로 생성된다")
    void createsInitialSuperAdmin() {
        var admin = adminUserRepository.findByLoginId("inittest").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(admin.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(adminUserRepository.count()).isEqualTo(1);

        // 평문이 저장되지 않았고, BCrypt 로 검증 가능해야 한다
        assertThat(admin.getPasswordHash()).isNotEqualTo("Init!Passw0rd").startsWith("$2");
        assertThat(passwordEncoder.matches("Init!Passw0rd", admin.getPasswordHash())).isTrue();
    }

    // ---------- 헬스체크 ----------

    @Test
    @DisplayName("GET /api/v1/health 가 인증 없이 200 과 기동 상태를 돌려준다")
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("hanseongge-api"))
                .andExpect(jsonPath("$.profile").value("test"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.schemaVersion").value("7"))
                .andExpect(jsonPath("$.roundingPolicy").value(RoundingPolicy.TEMPORARY_DEFAULT.name()))
                .andExpect(jsonPath("$.roundingPolicyTemporary").value(true))
                // A-1(부가세)도 미확정이므로 임시 정책이 그대로 노출된다
                .andExpect(jsonPath("$.vatMode").value(VatMode.TEMPORARY_DEFAULT.name()))
                .andExpect(jsonPath("$.vatPolicyTemporary").value(true))
                .andExpect(jsonPath("$.serverTime").exists());
    }

    // ---------- 보안 ----------

    @Test
    @DisplayName("관리자 API 는 인증 없이 접근할 수 없고 표준 에러 응답을 돌려준다")
    void adminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("미인증 요청에는 경로 존재 여부를 노출하지 않는다 — 없는 경로도 404 가 아니라 401")
    void unknownPathDoesNotLeakExistence() throws Exception {
        // /api/v1/** 는 인증이 필요하므로 보안 필터가 디스패처보다 먼저 막는다.
        // 404 와 401 을 구분해 주면 공격자가 엔드포인트 목록을 추측할 수 있으므로,
        // 둘 다 401 로 나가는 것이 의도된 동작이다.
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
