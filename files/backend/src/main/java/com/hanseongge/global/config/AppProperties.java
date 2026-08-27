package com.hanseongge.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * {@code app.*} 설정 바인딩.
 *
 * <p>민감정보는 여기에 기본값을 두지 않는다 — 환경변수 미주입 시 조용히 동작하는 대신
 * 기동에 실패해야 한다 (설계서 §14.4).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Cors cors,
        @DefaultValue AdminInit adminInit,
        @DefaultValue Jwt jwt,
        @DefaultValue AdminSecurity adminSecurity
) {

    public record Cors(
            @DefaultValue("http://localhost:3000") List<String> allowedOrigins
    ) {}

    /**
     * 최초 관리자 계정 부트스트랩 (STEP 3 요구사항 8).
     * 값이 비어 있으면 생성하지 않는다 — 기본 계정을 코드에 박아두지 않는다.
     */
    public record AdminInit(
            @DefaultValue("") String id,
            @DefaultValue("") String password
    ) {
        public boolean isConfigured() {
            return !id.isBlank() && !password.isBlank();
        }
    }

    /**
     * JWT 발급 설정 (설계서 §10, 개발계획 §7.1).
     *
     * <p>{@code secret} 기본값이 빈 문자열인 이유: prod 프로파일은
     * {@code app.jwt.secret: ${JWT_SECRET}} 로 기본값 없이 선언되어 있어 환경변수가
     * 없으면 <b>기동 자체가 실패</b>한다. local/test 에서만 빈 값이 들어올 수 있고,
     * 그때는 {@code AdminJwtProvider} 가 기동 시 1회용 랜덤 키를 만들고 WARN 을 남긴다.
     */
    public record Jwt(
            @DefaultValue("") String secret,
            @DefaultValue("hanseongge") String issuer,
            /** 관리자 Access 토큰 수명. 개발계획 §7.1 = 30분. */
            @DefaultValue("30") int adminAccessTtlMinutes,
            /** 관리자 Refresh 토큰 수명. 개발계획 §7.1 = 8시간. */
            @DefaultValue("8") int adminRefreshTtlHours
    ) {
        public boolean hasSecret() {
            return secret != null && !secret.isBlank();
        }
    }

    /**
     * 관리자 로그인 보호 설정.
     *
     * <p>계정 잠금과 Rate Limit 은 막는 대상이 다르다. 잠금은 <b>한 계정</b>을 노린
     * 비밀번호 추측을, Rate Limit 은 <b>한 IP</b> 가 여러 계정을 훑는 시도를 막는다.
     * 둘 중 하나만 있으면 다른 쪽 공격이 그대로 통과한다.
     */
    public record AdminSecurity(
            /** 연속 실패 임계치. 도달하면 status=LOCKED (설계서 §10). */
            @DefaultValue("5") int maxLoginFailures,
            /** 로그인 엔드포인트 IP당 분당 허용 횟수. */
            @DefaultValue("10") int loginRatePerMinute
    ) {}
}
