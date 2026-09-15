package com.hanseongge.domain.admin.service;

import com.hanseongge.domain.admin.entity.AdminOtpRecoveryCode;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminOtpRecoveryCodeRepository;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.auth.AdminOtpCipher;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.TotpGenerator;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 관리자 2단계 인증 (OTP).
 *
 * <h2>왜 IP 제한이 아니라 OTP 인가</h2>
 * 금값이 급변하면 관리자는 본사가 아니라 <b>휴대폰으로</b> 시세를 고친다. 휴대폰 IP 는 계속
 * 바뀌어 허용목록으로는 그 순간 접속이 막힌다. 그래서 "어디서"가 아니라 "누가"를 막는다 —
 * 비밀번호가 새도 그 사람의 휴대폰이 없으면 들어오지 못한다.
 *
 * <h2>지키는 것</h2>
 * <ul>
 *   <li><b>같은 6자리를 두 번 쓰지 못한다.</b> 어깨너머로 본 숫자를 30초 안에 다시 넣어도 거절된다</li>
 *   <li><b>OTP 실패는 비밀번호 실패와 따로 센다.</b> 함께 세면 비밀번호를 아는 사람이 로그인을
 *       반복해 카운터를 계속 되돌리며 6자리를 무한히 찍어 볼 수 있다</li>
 *   <li><b>키는 암호화해 둔다.</b> DB 가 새도 2단계 인증이 함께 무너지지 않게 한다</li>
 *   <li><b>휴대폰을 잃어도 갇히지 않는다.</b> 복구 코드 10개, 그리고 최고관리자의 초기화</li>
 * </ul>
 *
 * <p>로그인 흐름의 토큰 발급·예외 변환은 {@link AdminAuthService} 가 한다. 이 클래스는 실패
 * 기록이 롤백되지 않도록 예외 대신 결과를 돌려준다 ({@link AdminLoginAttemptService} 와 같은 이유).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOtpService {

    private static final int STEP_WINDOW = 1;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 8;
    /** 헷갈리는 글자(0/O, 1/I/L)를 뺐다. 종이에 옮겨 적는 코드다. */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final AdminOtpCipher cipher;
    private final AdminOtpRecoveryCodeRepository recoveryCodeRepository;
    private final AdminUserRepository adminUserRepository;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;
    private final SecureRandom random = new SecureRandom();

    /** 등록 화면에 보여줄 것. {@code secret} 은 사람이 옮겨 적기 좋게 4자리씩 띄운 값이다. */
    public record Setup(String secret, String otpauthUri, String issuer, String accountName) {
    }

    public enum VerifyResult {
        TOTP,
        RECOVERY_CODE,
        BAD_CODE,
        /** 이번 실패로 잠겼거나 이미 잠긴 계정 */
        LOCKED
    }

    /** @param recoveryCodesRemaining 복구 코드로 들어왔을 때만 의미가 있다 */
    public record Verification(VerifyResult result, long recoveryCodesRemaining) {
    }

    public record Status(boolean required, boolean enabled, OffsetDateTime enabledAt, long recoveryCodesRemaining) {
    }

    /** 모든 관리자에게 OTP 를 요구하는가. 꺼져 있어도 등록한 관리자에게는 묻는다. */
    public boolean required() {
        return appProperties.adminSecurity().otpRequired();
    }

    // =================================================================
    // 등록
    // =================================================================

    /**
     * 등록 시작 — 새 키를 만들어 암호화해 두고 인증 앱에 넣을 값을 돌려준다.
     *
     * <p>다시 부르면 키가 바뀐다. 앞서 찍은 QR 은 쓸 수 없게 되는데, 등록을 마치기 전이라 문제가
     * 없고 오히려 누군가 옆에서 찍어 간 QR 을 무효로 만든다.
     */
    @Transactional
    public Setup beginSetup(Long adminId) {
        AdminUser admin = load(adminId);
        if (admin.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_ALREADY_ENABLED,
                    "이미 OTP 를 쓰는 계정의 재등록 시도: " + admin.getLoginId());
        }

        byte[] secret = TotpGenerator.newSecret();
        admin.prepareOtp(cipher.encrypt(secret));

        String base32 = TotpGenerator.base32(secret);
        String issuer = appProperties.adminSecurity().otpIssuer();
        return new Setup(grouped(base32), otpauthUri(issuer, admin.getLoginId(), base32),
                issuer, admin.getLoginId());
    }

    /**
     * 등록 완료 — 앱에 뜬 6자리로 키가 제대로 들어갔는지 확인하고 켠다.
     *
     * <p>확인 없이 켜면, QR 을 잘못 찍은 관리자가 다음 로그인에서 영영 못 들어온다.
     *
     * @param completeLogin 로그인 도중의 첫 등록이면 true — 이 등록이 곧 2단계 통과다
     * @return 복구 코드 평문. 이 순간에만 나간다
     */
    @Transactional
    public List<String> activate(Long adminId, String code, boolean completeLogin, RequestInfo request) {
        AdminUser admin = load(adminId);
        if (admin.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_ALREADY_ENABLED,
                    "이미 OTP 를 쓰는 계정의 등록 확인: " + admin.getLoginId());
        }
        if (admin.getOtpSecret() == null) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_NOT_ENABLED,
                    "등록 시작 없이 확인 시도: " + admin.getLoginId());
        }

        OptionalLong step = matchTotp(admin, code);
        if (step.isEmpty() || !admin.acceptOtpStep(step.getAsLong())) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_INVALID,
                    "OTP 등록 확인 코드 불일치: " + admin.getLoginId());
        }
        admin.enableOtp();

        List<String> codes = issueRecoveryCodes(admin.getId());
        audit(AuditActions.ADMIN_OTP_ENABLED, admin, request, Map.of("recoveryCodes", codes.size()));
        log.info("[ADMIN-OTP] OTP 등록 loginId={}", admin.getLoginId());

        if (completeLogin) {
            completeLogin(admin, "TOTP", request);
        }
        return codes;
    }

    // =================================================================
    // 로그인 2단계
    // =================================================================

    /**
     * 6자리 또는 복구 코드 확인.
     *
     * <p>6자리 숫자면 인증 앱 코드로, 그 밖의 형태면 복구 코드로 본다. 실패하면 실패 횟수를
     * 올리고 임계치에서 잠근다 — 그 기록이 남아야 하므로 예외를 던지지 않는다.
     */
    @Transactional
    public Verification verify(Long adminId, String rawCode, RequestInfo request) {
        AdminUser admin = load(adminId);
        if (!admin.isActive()) {
            return new Verification(VerifyResult.LOCKED, 0);
        }
        if (!admin.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_NOT_ENABLED,
                    "OTP 미등록 계정의 2단계 확인: " + admin.getLoginId());
        }

        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");

        if (code.matches("\\d{" + TotpGenerator.DIGITS + "}")) {
            OptionalLong step = matchTotp(admin, code);
            if (step.isPresent() && admin.acceptOtpStep(step.getAsLong())) {
                completeLogin(admin, "TOTP", request);
                return new Verification(VerifyResult.TOTP, 0);
            }
        } else {
            Optional<AdminOtpRecoveryCode> found = recoveryCodeRepository
                    .findByAdminIdAndCodeDigestAndUsedAtIsNull(admin.getId(), cipher.digest(normalizeRecoveryCode(code)));
            if (found.isPresent()) {
                found.get().markUsed();
                recoveryCodeRepository.flush();
                admin.recordRecoveryCodeUse();
                long remaining = recoveryCodeRepository.countByAdminIdAndUsedAtIsNull(admin.getId());

                audit(AuditActions.ADMIN_OTP_RECOVERY_USED, admin, request, Map.of("remaining", remaining));
                log.warn("[ADMIN-OTP] 복구 코드로 로그인 loginId={} 남은 코드={}", admin.getLoginId(), remaining);
                completeLogin(admin, "RECOVERY_CODE", request);
                return new Verification(VerifyResult.RECOVERY_CODE, remaining);
            }
        }

        int max = appProperties.adminSecurity().maxOtpFailures();
        boolean nowLocked = admin.recordOtpFailure(max);
        audit(AuditActions.ADMIN_OTP_FAILED, admin, request,
                Map.of("otpFailCount", admin.getOtpFailCount(), "maxFailures", max));

        if (nowLocked) {
            log.warn("[ADMIN-OTP] OTP 연속 실패 {}회로 계정을 잠갔습니다. loginId={}", max, admin.getLoginId());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", admin.getStatus().name());
            after.put("reason", "OTP_FAILURES");
            after.put("otpFailCount", admin.getOtpFailCount());
            audit(AuditActions.ADMIN_ACCOUNT_AUTO_LOCKED, admin, request, after);
            return new Verification(VerifyResult.LOCKED, 0);
        }
        return new Verification(VerifyResult.BAD_CODE, 0);
    }

    // =================================================================
    // 내 계정
    // =================================================================

    @Transactional(readOnly = true)
    public Status status(Long adminId) {
        // 읽기 전용 트랜잭션에서는 행 잠금(SELECT FOR UPDATE)을 쓸 수 없다.
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND, "관리자 없음: " + adminId));
        return new Status(required(), admin.isOtpEnabled(), admin.getOtpEnabledAt(),
                admin.isOtpEnabled() ? recoveryCodeRepository.countByAdminIdAndUsedAtIsNull(adminId) : 0);
    }

    /**
     * 복구 코드 재발급. 지금 인증 앱의 6자리를 요구한다.
     *
     * <p>로그인된 세션만으로 재발급하게 두면, 잠시 자리를 비운 PC 에서 누군가 복구 코드를 받아
     * 가 휴대폰 없이도 들어올 수 있게 된다.
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(Long adminId, String code, RequestInfo request) {
        AdminUser admin = load(adminId);
        if (!admin.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_NOT_ENABLED,
                    "OTP 미등록 계정의 복구 코드 재발급: " + admin.getLoginId());
        }
        OptionalLong step = matchTotp(admin, code == null ? "" : code.replaceAll("\\s", ""));
        if (step.isEmpty() || !admin.acceptOtpStep(step.getAsLong())) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_INVALID,
                    "복구 코드 재발급 확인 코드 불일치: " + admin.getLoginId());
        }

        List<String> codes = issueRecoveryCodes(admin.getId());
        audit(AuditActions.ADMIN_OTP_RECOVERY_REGENERATED, admin, request, Map.of("recoveryCodes", codes.size()));
        return codes;
    }

    // =================================================================
    // 최고관리자
    // =================================================================

    /**
     * OTP 초기화 — 휴대폰을 잃어버린 관리자를 위해. 권한·본인 여부·감사는 호출부가 맡는다.
     *
     * <p>토큰 세대를 올려 대상의 기존 세션도 끊는다. 휴대폰을 잃었다는 것은 그 휴대폰에 열려 있던
     * 관리자 화면도 남의 손에 있을 수 있다는 뜻이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(AdminUser admin) {
        recoveryCodeRepository.deleteAllByAdminId(admin.getId());
        admin.resetOtp();
        log.warn("[ADMIN-OTP] OTP 초기화 loginId={}", admin.getLoginId());
    }

    // =================================================================
    // 내부
    // =================================================================

    private OptionalLong matchTotp(AdminUser admin, String code) {
        byte[] secret = cipher.decrypt(admin.getOtpSecret());
        return TotpGenerator.match(secret, code, TotpGenerator.currentStep(), STEP_WINDOW);
    }

    private void completeLogin(AdminUser admin, String secondFactor, RequestInfo request) {
        admin.recordLogin();
        audit(AuditActions.ADMIN_LOGIN, admin, request,
                Map.of("role", admin.getRole().name(), "secondFactor", secondFactor));
    }

    private List<String> issueRecoveryCodes(Long adminId) {
        recoveryCodeRepository.deleteAllByAdminId(adminId);
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < RECOVERY_CODE_COUNT) {
            codes.add(newRecoveryCode());
        }
        for (String code : codes) {
            recoveryCodeRepository.save(AdminOtpRecoveryCode.issue(adminId, cipher.digest(normalizeRecoveryCode(code))));
        }
        return List.copyOf(codes);
    }

    /** "ABCD-EFGH" — 8글자, 40비트 */
    private String newRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i += 1) {
            if (i == RECOVERY_CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    /** 대소문자·하이픈·공백을 가리지 않는다. 사람이 옮겨 적은 코드다. */
    static String normalizeRecoveryCode(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String grouped(String base32) {
        return base32.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static String otpauthUri(String issuer, String account, String base32) {
        return "otpauth://totp/" + encode(issuer) + ":" + encode(account)
                + "?secret=" + base32
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + TotpGenerator.DIGITS
                + "&period=" + TotpGenerator.PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 행을 잠그고 읽는다 — 같은 6자리를 동시에 보낸 두 요청이 함께 통과하지 못하게. */
    private AdminUser load(Long adminId) {
        return adminUserRepository.findForUpdate(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND, "관리자 없음: " + adminId));
    }

    private void audit(String action, AdminUser admin, RequestInfo request, Map<String, ?> after) {
        AuditRecord.Builder builder = AuditRecord.builder(action)
                .actor(ActorType.ADMIN, admin.getLoginId())
                .target(AuditActions.TARGET_ADMIN_USER, String.valueOf(admin.getId()))
                .after(after);
        if (request != null) {
            builder.request(request.ip(), request.userAgent());
        }
        auditLogService.record(builder.build());
    }
}
