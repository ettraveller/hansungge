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
 * <p>검증 범위: Flyway V1~V20 적용 → 스키마 정합성(ddl-auto=validate) → 설정 캐시 →
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
    @DisplayName("Flyway V1~V20 이 적용되고 42개 테이블이 생성된다")
    void flywayAppliesAllMigrations() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);
        // V6(관리자 인증)은 컬럼·인덱스만 추가한다. V7 은 market_price_fetch_logs 1개,
        // V8 은 잔여 결함 11건을 해소하며 7개 테이블을 더한다.
        // V9(약관 버전)·V11(반올림 정책)은 설정 행만 다루고, V10 은 장바구니 컬럼만
        // 보강하므로 셋 다 테이블 수를 늘리지 않는다.
        // V12(게시판)는 V5 가 세워둔 notices/events/qnas 에 컬럼을 더하고
        // event_entries 1개만 새로 만든다 → 40 + 1.
        // V13(자체 가입·본인확인)은 users 에 컬럼을 더하고
        // identity_verifications · email_verifications 2개를 새로 만든다 → 41 + 2.
        // V14(자동고시)는 market_price_raw 에 price_side, market_price_policies 에
        // auto_quote 컬럼을 더하고 설정·원천 행만 넣는다 — 테이블은 늘지 않는다.
        // V15(상품 시세문의)는 products 의 CHECK 제약 두 개만 다시 건다.
        // V16 은 설정 설명 문구만, V17 은 수집 원천 이름만 고친다.
        // V18 은 PRICE_STALE_MINUTES 를 0 으로 내린다 — 마지막 고시값을 계속 쓴다.
        // V19 는 자동 수집을 걷어내며 승인 대기·수집 로그 2개를 지운다.
        // V20(관리자 OTP)은 admin_users 에 컬럼을 더하고 복구 코드 1개를 새로 만든다 → 41 + 1.
        // V21(휴대폰 로그인)은 users 에 컬럼·제약·인덱스만 더한다.
        // V22 는 email_verifications 를 verification_codes 로 이름만 바꾼다 — 둘 다 테이블 수는 그대로.
        // V23(간편인증·CI 해시)은 컬럼만 바꾼다 — 테이블 수는 그대로.
        assertThat(versions)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
                        "16", "17", "18", "19", "20", "21", "22", "23");

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tableCount).isEqualTo(42);
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
        assertThat(systemSettingService.find("PRICE_STALE_MINUTES"))
                .as("V19 가 지웠다 — 고시값은 시간이 지나도 내려가지 않는다")
                .isEmpty();
    }

    @Test
    @DisplayName("미확정('TBD') 설정은 값이 없는 것으로 취급된다 — 예외를 던지지 않는다")
    void undecidedSettingIsTreatedAsAbsent() {
        // 부가세(A-1)는 아직 미확정이라 'TBD' 이며, 값이 없는 것으로 취급된다.
        assertThat(systemSettingService.find("VAT_MODE")).isEmpty();
    }

    @Test
    @DisplayName("확정된 반올림 정책(A-2)이 스키마에 반영되어 있다 — 임시 기본값으로 계산되지 않는다")
    void confirmedRoundingPolicyIsSeeded() {
        // V11 이 넣는다. 이 값이 없으면 임시 기본값(WON_FLOOR)으로 계산되어
        // 확정 정책과 다른 금액이 조용히 고객에게 나간다.
        assertThat(systemSettingService.find(PriceRoundingResolver.SETTING_KEY))
                .contains("CEIL_100");
        assertThat(priceRoundingResolver.resolve()).isEqualTo(RoundingPolicy.CEIL_100);
        assertThat(priceRoundingResolver.isUsingTemporaryDefault()).isFalse();
    }

    @Test
    @DisplayName("설정을 바꾸면 캐시가 무효화되어 새 값이 읽힌다")
    void updateEvictsCache() {
        try {
            assertThat(priceRoundingResolver.resolve()).isEqualTo(RoundingPolicy.CEIL_100);

            systemSettingService.update("PRICE_ROUNDING_MODE", "FLOOR_100", "test");

            assertThat(systemSettingService.find("PRICE_ROUNDING_MODE")).contains("FLOOR_100");
            assertThat(priceRoundingResolver.resolve()).isEqualTo(RoundingPolicy.FLOOR_100);
            assertThat(priceRoundingResolver.isUsingTemporaryDefault()).isFalse();
        } finally {
            // 다른 테스트에 영향을 주지 않도록 확정값으로 되돌린다
            systemSettingService.update("PRICE_ROUNDING_MODE", "CEIL_100", "test");
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
                .andExpect(jsonPath("$.schemaVersion").value("20"))
                // A-2 확정값이 V11 로 반영되어 있으므로 임시 정책이 아니다.
                .andExpect(jsonPath("$.roundingPolicy").value(RoundingPolicy.CEIL_100.name()))
                .andExpect(jsonPath("$.roundingPolicyTemporary").value(false))
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
