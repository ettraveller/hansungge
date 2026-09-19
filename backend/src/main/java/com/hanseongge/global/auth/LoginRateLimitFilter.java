package com.hanseongge.global.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hanseongge.global.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * 로그인 계열 엔드포인트 Rate Limit (Bucket4j, 설계서 §10 보안 체크리스트).
 *
 * <p>관리자 체인과 회원 체인이 <b>각자 인스턴스</b>를 쓴다 — 지키는 경로와 한도가 다르다.
 * 회원 쪽은 문자 발송이 들어 있어서 특히 중요하다. 번호만 알면 누구나 요청할 수 있으므로,
 * 이 제한이 없으면 한 IP 가 번호를 바꿔 가며 우리 발신번호로 문자 폭탄을 보낼 수 있다
 * (번호당 한도는 {@code VerificationCodeService} 가 따로 건다).
 *
 * <h2>계정 잠금과 역할이 다르다</h2>
 * 5회 실패 잠금은 <b>한 계정</b>을 노린 비밀번호 추측을 막는다. 하지만 공격자가
 * 계정마다 4번씩만 시도하며 아이디를 훑으면 아무 계정도 잠기지 않는다. 그 경로를
 * 막는 것이 IP 단위 제한이다. 둘은 대체재가 아니라 보완재다.
 *
 * <h2>인메모리인 이유와 한계</h2>
 * 버킷을 Caffeine 에 들고 있으므로 <b>인스턴스가 늘어나면 인스턴스 수만큼 한도가 늘어난다.</b>
 * 현재 단일 인스턴스 운영이라 문제가 없고, 설계서 §7.3 의 "Redis 는 트래픽 발생 후" 원칙과도
 * 맞다. 다중화 시점에 Bucket4j 의 분산 백엔드로 교체한다 — 그때 바꿀 것은 이 클래스뿐이다.
 * 그 전까지는 Nginx 의 {@code /api/admin/**} IP 허용목록이 1차 방어선이다.
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /**
     * 관리자 쪽 제한 경로. 6자리는 경우의 수가 백만뿐이라 OTP 확인·등록도 막아야 한다.
     * 버킷은 경로마다 따로 둔다 — 로그인 몇 번에 OTP 입력 기회가 줄어들면 정상 사용자가 막힌다.
     */
    public static final Set<String> ADMIN_PATHS = Set.of(
            "/api/admin/auth/login",
            "/api/admin/auth/otp/verify",
            "/api/admin/auth/otp/activate");

    /**
     * 회원 쪽 제한 경로.
     *
     * <p>비밀번호·인증번호를 맞춰 보는 곳(로그인·문자 로그인·비밀번호 변경), 비용이 드는
     * 곳(문자·메일 발송, 본인확인 건당 과금)을 막는다.
     */
    public static final Set<String> MEMBER_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/sms/send",
            "/api/v1/auth/sms/login",
            "/api/v1/auth/email/verification",
            "/api/v1/auth/identity/start",
            "/api/v1/auth/password/reset/identity/start",
            "/api/v1/me/password");

    private final Set<String> guardedPaths;
    private final int permitsPerMinute;
    private final Cache<String, Bucket> buckets;

    public LoginRateLimitFilter(Set<String> guardedPaths, int permitsPerMinute) {
        this.guardedPaths = guardedPaths;
        this.permitsPerMinute = permitsPerMinute;
        this.buckets = Caffeine.newBuilder()
                // 마지막 접근 후 2분이면 버킷이 어차피 가득 찬다. 더 들고 있을 이유가 없다.
                .expireAfterAccess(Duration.ofMinutes(2))
                // 상한을 두지 않으면 조작된 X-Forwarded-For 로 메모리를 밀어 넣을 수 있다.
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                && guardedPaths.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip = RequestMetadata.ip(request);
        String key = ip == null ? "unknown" : ip;

        Bucket bucket = buckets.get(request.getRequestURI() + "|" + key, k -> newBucket());
        if (bucket != null && bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        // 남은 대기 시간을 알려준다. 정상 사용자가 언제 다시 시도할지 알 수 있어야 한다.
        response.setHeader("Retry-After", "60");
        log.warn("[RATE-LIMIT] 로그인 계열 요청 제한 초과. path={} ip={} limit={}/분",
                request.getRequestURI(), key, permitsPerMinute);
        SecurityResponseWriter.write(response, ErrorCode.TOO_MANY_REQUESTS);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(permitsPerMinute)
                // greedy: 1분에 걸쳐 조금씩 회복한다. intervally 로 두면 정각마다
                // 한도가 통째로 풀려 그 순간에 시도가 몰린다.
                .refillGreedy(permitsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
