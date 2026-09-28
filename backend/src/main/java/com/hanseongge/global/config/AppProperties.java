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
        @DefaultValue Mail mail,
        @DefaultValue Identity identity,
        @DefaultValue Sms sms,
        @DefaultValue Payment payment
) {

    /**
     * 가입·로그인 정책.
     *
     * <p>비밀값이 없다. 여기 있는 것은 전부 정책값이라 기본값을 두는 것이 안전하다 —
     * 빠뜨렸다고 기동을 막을 이유가 없고, 막으면 오히려 배포가 어려워진다.
     */
    public record SignUp(
            /** 인증번호(메일·문자) 유효 시간. 짧으면 늦게 도착할 때 못 쓰고, 길면 훑을 시간을 준다. */
            @DefaultValue("5") int codeTtlMinutes,
            /** 인증번호 시도 허용 횟수. 6자리 숫자는 막지 않으면 만료 전에 다 훑을 수 있다. */
            @DefaultValue("5") int codeMaxAttempts,
            /** 같은 곳으로 인증번호를 다시 받기까지 기다릴 시간(초). 메일·문자 폭탄을 막는다. */
            @DefaultValue("60") int codeResendCooldownSeconds,
            /**
             * 한 번호로 한 시간에 보낼 수 있는 문자 수.
             *
             * <p>분당 1회 쿨다운만으로는 한 시간에 60통이 나간다. 번호만 알면 누구나 요청할 수
             * 있으므로, 이것이 없으면 우리 발신번호가 남의 휴대폰을 괴롭히는 도구가 된다.
             */
            @DefaultValue("5") int smsHourlyLimit,
            /** 본인확인 결과를 가입·재설정에 쓸 수 있는 시간. 창을 열어둔 채 방치한 건은 버린다. */
            @DefaultValue("10") int identityTtlMinutes,
            /** 본인확인 뒤 새 비밀번호를 정하기까지 허용하는 시간. */
            @DefaultValue("5") int passwordResetTtlMinutes,
            /** 회원 로그인 연속 실패 임계치. 도달하면 30분 잠긴다. */
            @DefaultValue("5") int maxLoginFailures,
            /**
             * 회원 인증 엔드포인트(로그인·문자 발송·본인확인 시작 등)의 IP당 분당 허용 횟수.
             *
             * <p>계정 잠금은 <b>한 계정</b>을, 이것은 <b>한 IP</b> 가 여러 번호를 훑는 시도를 막는다.
             */
            @DefaultValue("10") int authRatePerMinute
    ) {}

    /**
     * 본인인증 — KG이니시스 통합인증 간편인증 (TODO E-4).
     *
     * <p>{@code provider} 는 {@code MOCK | INICIS} 중 하나이거나 비어 있다. 비어 있으면
     * 본인인증을 쓸 수 없고 <b>자체(휴대폰) 가입이 닫힌다</b> — 소셜 가입과 로그인은 그대로 된다.
     *
     * <p>{@code MOCK} 은 아무것도 확인하지 않는다. 운영 프로필에서 MOCK 이면 기동이 실패한다
     * ({@code MockIdentityVerificationProvider}).
     *
     * <p>이니시스 계약 전에는 공식 샘플의 <b>테스트 상점아이디</b>({@code INIiasTest})로 실제
     * 인증창까지 붙여 볼 수 있다. 테스트 값은 코드에 두지 않고 환경변수로만 넣는다 —
     * 운영에 테스트 MID 가 박힌 채 나가면 아무도 눈치채지 못한다.
     */
    public record Identity(
            @DefaultValue("") String provider,
            /** 이니시스 상점아이디(MID) */
            @DefaultValue("") String mid,
            /** 이니시스 apiKey. authHash 계산에 쓰는 비밀값 */
            @DefaultValue("") String apiKey,
            /**
             * 이니시스 요청구분코드. {@code 01} 간편인증(PASS·카카오·네이버·토스·은행 — CI 제공),
             * {@code 03} 본인확인(은행·토스만 — DI 제공). 우리는 CI 로 판정하므로 {@code 01}.
             */
            @DefaultValue("01") String reqSvcCd,
            /** 이니시스 결과 복호화 IV. 규격 고정값이라 바꿀 일이 없다 — 규격이 바뀌었을 때만. */
            @DefaultValue("SASKGINICIS00000") String seedIv,
            /**
             * CI 해시용 서버 고정 비밀값 (pepper). <b>한 번 정하면 바꾸지 않는다</b> — 바꾸면 이미 저장된
             * 판정 키와 대조가 전부 끊겨, 기존 회원을 같은 사람으로 알아보지 못한다.
             *
             * <p>비어 있으면 본인인증을 쓸 수 없다(자체 가입이 닫힌다). 생성: {@code openssl rand -base64 48}
             */
            @DefaultValue("") String hashPepper,
            /**
             * 공급자가 결과를 돌려보낼 <b>백엔드</b> 주소.
             *
             * <p>이니시스는 결과를 브라우저 폼 POST 로 보낸다. 화면(Next.js 페이지)은 POST 를
             * 받을 수 없어서, 백엔드가 받아 화면 주소({@link #returnPageUrl})로 넘긴다.
             */
            @DefaultValue("http://localhost:8080/api/v1/auth/identity/return") String callbackUrl,
            /** 본인확인을 마친 사용자가 도착할 화면. */
            @DefaultValue("http://localhost:3000/auth/identity") String returnPageUrl,
            /**
             * 개발용 목이 돌려줄 사람 번호. 같은 값이면 같은 사람(같은 CI·같은 휴대폰)이다.
             *
             * <p>고정값이어야 "이미 가입한 사람이 다시 가입" 같은 경우를 로컬에서 재현할 수 있다.
             * 다른 사람으로 가입해 보려면 이 값을 바꿔 재기동한다.
             */
            @DefaultValue("1") int mockPersona
    ) {
        public boolean is(String code) {
            return provider != null && provider.equalsIgnoreCase(code);
        }
    }

    /**
     * NCP SENS 문자 발송.
     *
     * <p>비어 있으면 인증번호 로그인만 꺼진다. 발신번호는 NCP 에 <b>사전 등록</b>해야 하고,
     * 등록되지 않은 번호로 보내면 공급자가 거부한다.
     */
    public record Sms(
            @DefaultValue("") String accessKey,
            @DefaultValue("") String secretKey,
            @DefaultValue("") String serviceId,
            @DefaultValue("") String senderPhone,
            @DefaultValue("5") int connectTimeoutSeconds,
            @DefaultValue("10") int readTimeoutSeconds
    ) {
        public boolean isConfigured() {
            return !accessKey.isBlank() && !secretKey.isBlank()
                    && !serviceId.isBlank() && !senderPhone.isBlank();
        }
    }

    /**
     * 메일 발송 설정.
     *
     * <p>{@code accessKey}·{@code secretKey} 는 비밀값이라 기본값을 두지 않는다. 비어 있어도
     * {@code NcpOutboundMailSender} 는 빈으로 등록되고, 기동 로그에 경고를 남긴 뒤
     * <b>발송 시점에</b> 실패한다 — 메일이 없다고 서비스 전체가 못 뜨면 안 되기 때문이다.
     */
    /**
     * 결제 (토스페이먼츠, STEP 14).
     *
     * <p>메일·소셜과 같은 판단으로 기본값을 둔다 — 비어 있으면 기동은 되되 결제만 닫히고,
     * 기동 로그에 무엇이 없는지가 남는다. 결제 키가 없다고 시세 조회까지 멈추면 더 나쁘다.
     *
     * <p>⚠️ {@code clientKey} 는 <b>브라우저에 그대로 나간다</b> — 그렇게 쓰라고 있는 값이다.
     * {@code secretKey} 는 절대 나가면 안 된다. 그것 하나로 승인·취소를 마음대로 부를 수 있다.
     */
    public record Payment(
            @DefaultValue("") String clientKey,
            @DefaultValue("") String secretKey,
            @DefaultValue("5") int connectTimeoutSeconds,
            @DefaultValue("15") int readTimeoutSeconds
    ) {
        public boolean isConfigured() {
            return !clientKey.isBlank() && !secretKey.isBlank();
        }

        /** 테스트 키인가. 화면에 "테스트 결제" 안내를 띄울지 판단하는 데 쓴다. */
        public boolean isTestKey() {
            return clientKey.startsWith("test_");
        }
    }

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
