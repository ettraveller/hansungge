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
        @DefaultValue AdminSecurity adminSecurity,
        @DefaultValue OAuth oauth,
        @DefaultValue SignUp signUp,
        @DefaultValue Mail mail
) {

    /**
     * 자체 가입·본인확인 설정.
     *
     * <p>비밀값이 없다. 여기 있는 것은 전부 정책값이라 기본값을 두는 것이 안전하다 —
     * 빠뜨렸다고 기동을 막을 이유가 없고, 막으면 오히려 배포가 어려워진다.
     */
    public record SignUp(
            /** 이메일 인증번호 유효 시간. 짧으면 메일이 늦게 올 때 못 쓰고, 길면 훑을 시간을 준다. */
            @DefaultValue("5") int emailCodeTtlMinutes,
            /** 인증번호 시도 허용 횟수. 6자리 숫자는 막지 않으면 만료 전에 다 훑을 수 있다. */
            @DefaultValue("5") int emailCodeMaxAttempts,
            /** 같은 주소로 인증번호를 다시 받기까지 기다릴 시간(초). 메일 폭탄을 막는다. */
            @DefaultValue("60") int emailCodeResendCooldownSeconds,
            /** 본인확인 결과를 가입에 쓸 수 있는 시간. 창을 열어둔 채 방치한 건은 버린다. */
            @DefaultValue("30") int identityTtlMinutes,
            /** 비밀번호 재설정 링크 유효 시간. */
            @DefaultValue("30") int passwordResetTtlMinutes,
            /** 회원 로그인 연속 실패 임계치. 도달하면 30분 잠긴다. */
            @DefaultValue("5") int maxLoginFailures
    ) {}

    /**
     * 메일 발송 설정.
     *
     * <p>{@code accessKey}·{@code secretKey} 는 비밀값이라 기본값을 두지 않는다. 대신
     * 비어 있으면 {@code NcpOutboundMailSender} 가 빈으로 등록되지 않아, 운영에서
     * 메일 기능만 조용히 죽는 대신 기동 로그에 경고가 남는다.
     */
    public record Mail(
            @DefaultValue("") String accessKey,
            @DefaultValue("") String secretKey,
            /** 보내는 주소. SPF/DKIM/DMARC 가 이 도메인에 설정되어 있어야 스팸함으로 가지 않는다. */
            @DefaultValue("") String fromAddress,
            @DefaultValue("한성금거래소") String fromName,
            @DefaultValue("5") int connectTimeoutSeconds,
            @DefaultValue("10") int readTimeoutSeconds
    ) {
        public boolean isConfigured() {
            return !accessKey.isBlank() && !secretKey.isBlank() && !fromAddress.isBlank();
        }
    }

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
            @DefaultValue("8") int adminRefreshTtlHours,
            /** 회원 Access 토큰 수명. 개발계획 §7.1 = 30분. */
            @DefaultValue("30") int userAccessTtlMinutes,
            /**
             * 회원 Refresh 토큰 수명. 개발계획 §7.1 = 14일.
             * 관리자(8시간)보다 훨씬 긴 이유는 쇼핑몰에서 매번 재로그인을 요구하면
             * 이탈이 커지기 때문이다. 대신 회원 Refresh 는 DB 에 남아 즉시 회수할 수 있다.
             */
            @DefaultValue("14") int userRefreshTtlDays
    ) {
        public boolean hasSecret() {
            return secret != null && !secret.isBlank();
        }
    }

    /**
     * 소셜 로그인 설정 (개발계획 §7.1).
     *
     * <p>클라이언트 시크릿에 기본값을 두지 않는다. 값이 비어 있으면 그 제공자가
     * "사용 불가"로 표시될 뿐 기동은 정상이다 — 앱 등록·검수(TODO A-4)가 끝나지 않은
     * 제공자 때문에 서비스 전체가 못 뜨면 안 되기 때문이다.
     */
    public record OAuth(
            /**
             * 제공자가 인가코드를 돌려보낼 <b>프론트엔드</b> 주소의 앞부분.
             * 최종 redirect_uri 는 {@code {base}/{provider 소문자}} 이며,
             * 제공자 콘솔에 등록한 값과 <b>글자 하나까지 같아야</b> 한다.
             */
            @DefaultValue("http://localhost:3000/auth/callback") String redirectBaseUrl,
            /** state 토큰 수명. 사용자가 제공자 로그인 화면에 머무는 시간을 감안한다. */
            @DefaultValue("10") int stateTtlMinutes,
            /** 약관 동의를 마치기까지 허용하는 시간. */
            @DefaultValue("15") int signUpTokenTtlMinutes,
            /** 외부 호출 연결/응답 제한(초). 소셜 서버가 늦으면 로그인 요청이 통째로 묶인다. */
            @DefaultValue("5") int connectTimeoutSeconds,
            @DefaultValue("8") int readTimeoutSeconds,
            /**
             * Refresh 쿠키에 Secure 를 붙일지. 운영은 반드시 true 다.
             * 개발은 http://localhost 라 true 면 쿠키가 아예 저장되지 않는다.
             */
            @DefaultValue("false") boolean refreshCookieSecure,
            /**
             * Refresh 쿠키 도메인. 비우면 host-only 다.
             * 운영에서 www 와 api 가 서브도메인으로 갈리면 {@code .hanseongge.com} 을 준다 —
             * 그래야 같은 사이트로 취급되어 SameSite=Lax 쿠키가 API 요청에 실린다.
             */
            @DefaultValue("") String refreshCookieDomain,
            @DefaultValue OAuthClient google,
            @DefaultValue OAuthClient naver,
            @DefaultValue OAuthClient kakao
    ) {}

    public record OAuthClient(
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret
    ) {
        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
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
            /** 로그인 엔드포인트 IP당 분당 허용 횟수. OTP 확인·등록에도 따로 같은 한도를 건다. */
            @DefaultValue("10") int loginRatePerMinute,
            /**
             * 모든 관리자에게 OTP 를 요구하는가. 운영은 반드시 true 다.
             * false 여도 OTP 를 등록한 관리자에게는 묻는다.
             */
            @DefaultValue("true") boolean otpRequired,
            /** OTP 연속 실패 임계치. 비밀번호 실패와 따로 센다. */
            @DefaultValue("5") int maxOtpFailures,
            /**
             * OTP 키 암호화 원본 키 (base64, 32바이트 이상). 비우면 JWT 서명키에서 파생한다 —
             * 그 경우 JWT_SECRET 을 바꾸면 등록된 OTP 를 전부 초기화해야 한다.
             */
            @DefaultValue("") String otpEncryptionKey,
            /** 인증 앱에 표시될 이름. */
            @DefaultValue("한성금거래소 관리자") String otpIssuer
    ) {}
}
