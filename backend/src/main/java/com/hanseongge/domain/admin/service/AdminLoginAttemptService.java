package com.hanseongge.domain.admin.service;

import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 로그인 시도 처리 — 자격 검증, 실패 누적, 자동 잠금, 감사 기록.
 *
 * <h2>왜 예외를 던지지 않고 결과를 돌려주는가</h2>
 * 로그인 실패는 <b>DB 를 변경하면서 실패하는</b> 드문 경우다(실패 횟수 +1, 임계치 도달 시
 * 잠금). 이 메서드 안에서 예외를 던지면 그 변경이 통째로 롤백되어 <b>아무리 시도해도
 * 계정이 잠기지 않는다.</b> 그래서 여기서는 결과만 돌려주고, 예외 변환은 호출자
 * ({@link AdminAuthService})가 트랜잭션 밖에서 한다.
 *
 * <h2>OTP 가 뒤따르면 여기서 로그인이 끝나지 않는다</h2>
 * 비밀번호가 맞아도 OTP 를 통과하기 전까지는 "로그인 성공"(ADMIN_LOGIN)을 남기지 않고
 * 마지막 로그인 시각도 바꾸지 않는다. 비밀번호 실패 횟수만 되돌린다 — OTP 실패는 따로 센다.
 *
 * <p>감사로그는 {@link AuditLogService#record} 가 별도 트랜잭션으로 쓰므로 이 규칙과
 * 무관하게 항상 남는다. 그래도 실패 카운터까지 안전하려면 위 구조가 필요하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLoginAttemptService {

    /** 로그인 시도 결과. 호출자가 응답/예외로 번역한다. */
    public enum Result {
        SUCCESS,
        /** 아이디가 없거나 비밀번호가 틀림 — 응답에서는 구분하지 않는다. */
        BAD_CREDENTIALS,
        /** 계정이 LOCKED / DISABLED 상태 */
        BLOCKED
    }

    public record Outcome(Result result, AdminUser admin) {

        public boolean isSuccess() {
            return result == Result.SUCCESS;
        }
    }

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;

    /**
     * @param otpRequired 모든 관리자에게 OTP 를 요구하는가. 꺼져 있어도 OTP 를 등록한 계정은
     *                    2단계를 거친다
     */
    @Transactional
    public Outcome attempt(String loginId, String rawPassword, RequestInfo request, boolean otpRequired) {
        Optional<AdminUser> found = adminUserRepository.findByLoginId(loginId);

        if (found.isEmpty()) {
            // 존재하지 않는 아이디. 비밀번호 검증을 건너뛰면 응답이 눈에 띄게 빨라져
            // 아이디 존재 여부가 시간차로 새어 나간다. 더미 해시로 같은 비용을 치른다.
            passwordEncoder.matches(rawPassword, dummyHash());
            recordUnknownLoginId(loginId, request);
            return new Outcome(Result.BAD_CREDENTIALS, null);
        }

        AdminUser admin = found.get();

        if (!admin.isActive()) {
            // 잠긴 계정은 비밀번호가 맞아도 통과시키지 않는다. 실패 횟수도 올리지 않는다 —
            // 이미 잠긴 계정의 카운터를 더 올려봐야 의미가 없다.
            auditLogService.record(base(AuditActions.ADMIN_LOGIN_BLOCKED, admin, request)
                    .after(Map.of("status", admin.getStatus().name()))
                    .build());
            return new Outcome(Result.BLOCKED, admin);
        }

        if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            int max = appProperties.adminSecurity().maxLoginFailures();
            boolean nowLocked = admin.recordLoginFailure(max);

            auditLogService.record(base(AuditActions.ADMIN_LOGIN_FAILED, admin, request)
                    .after(Map.of("failCount", admin.getLoginFailCount(), "maxFailures", max))
                    .build());

            if (nowLocked) {
                log.warn("[ADMIN-AUTH] 연속 실패 {}회로 계정을 잠갔습니다. loginId={}", max, admin.getLoginId());
                auditLogService.record(base(AuditActions.ADMIN_ACCOUNT_AUTO_LOCKED, admin, request)
                        .before(Map.of("status", "ACTIVE"))
                        .after(Map.of("status", admin.getStatus().name(), "failCount", admin.getLoginFailCount()))
                        .build());
            }
            return new Outcome(Result.BAD_CREDENTIALS, admin);
        }

        int previousFailCount = admin.getLoginFailCount();

        if (otpRequired || admin.isOtpEnabled()) {
            admin.recordPasswordVerified();
            auditLogService.record(base(AuditActions.ADMIN_LOGIN_PASSWORD_VERIFIED, admin, request)
                    .after(Map.of("otpEnabled", admin.isOtpEnabled(), "clearedFailCount", previousFailCount))
                    .build());
            return new Outcome(Result.SUCCESS, admin);
        }

        admin.recordLogin();
        auditLogService.record(base(AuditActions.ADMIN_LOGIN, admin, request)
                .after(Map.of("role", admin.getRole().name(), "clearedFailCount", previousFailCount))
                .build());

        return new Outcome(Result.SUCCESS, admin);
    }

    private void recordUnknownLoginId(String loginId, RequestInfo request) {
        auditLogService.record(AuditRecord.builder(AuditActions.ADMIN_LOGIN_FAILED)
                // 검증되지 않은 자칭 아이디다. 그래도 actor 에 남긴다 —
                // "누구 행세를 하려 했는가"가 감사에서 가장 자주 찾는 값이다.
                .actor(ActorType.ADMIN, loginId)
                .target(AuditActions.TARGET_LOGIN_ID, null)
                .after(Map.of("reason", "UNKNOWN_LOGIN_ID"))
                .request(request.ip(), request.userAgent())
                .build());
    }

    private AuditRecord.Builder base(String action, AdminUser admin, RequestInfo request) {
        return AuditRecord.builder(action)
                .actor(ActorType.ADMIN, admin.getLoginId())
                .target(AuditActions.TARGET_ADMIN_USER, String.valueOf(admin.getId()))
                .request(request.ip(), request.userAgent());
    }

    /**
     * 존재하지 않는 계정에 대해서도 BCrypt 검증 비용을 치르기 위한 더미 해시.
     * cost 12 짜리 실제 형식이어야 검증 시간이 비슷해진다.
     */
    private String dummyHash() {
        return "$2a$12$C6UzMDM.H6dfI/f/IKcEe.4ZoQ6TZ0m1nQ8V3lJmYtN0rM8fSg3rW";
    }
}
