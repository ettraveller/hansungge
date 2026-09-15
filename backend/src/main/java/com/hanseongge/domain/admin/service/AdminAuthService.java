package com.hanseongge.domain.admin.service;

import com.hanseongge.domain.admin.dto.AdminLoginRequest;
import com.hanseongge.domain.admin.dto.AdminLoginResponse;
import com.hanseongge.domain.admin.dto.AdminOtpActivateResponse;
import com.hanseongge.domain.admin.dto.AdminOtpRecoveryCodesResponse;
import com.hanseongge.domain.admin.dto.AdminOtpSetupResponse;
import com.hanseongge.domain.admin.dto.AdminOtpStatusResponse;
import com.hanseongge.domain.admin.dto.AdminUserResponse;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.auth.AdminJwtProvider;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.auth.AdminTokenClaims;
import com.hanseongge.global.auth.AdminTokenType;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 관리자 인증 (설계서 §10).
 *
 * <h2>로그인은 두 단계다</h2>
 * <pre>
 * 아이디·비밀번호 ──► OTP 를 등록했으면  OTP_REQUIRED       + 2단계 표 ──► /otp/verify   ──► 토큰
 *                  └► 아직이면(필수일 때) OTP_SETUP_REQUIRED + 2단계 표 ──► /otp/setup·activate ──► 토큰
 * </pre>
 * 2단계 표는 짧게 유효한 JWT 이고 관리자 API 에는 쓸 수 없다(필터가 ACCESS 만 받는다).
 * 비밀번호만 맞힌 사람이 할 수 있는 일은 6자리를 넣는 것뿐이다.
 *
 * <p><b>클래스 레벨 {@code @Transactional} 을 두지 않는다.</b> 로그인·OTP 실패 경로는
 * "DB 를 변경하고 나서 예외를 던져야" 하는데, 하나의 트랜잭션으로 감싸면 그 변경이
 * 롤백된다. 실제 DB 변경은 {@link AdminLoginAttemptService}·{@link AdminOtpService} 안에서
 * 끝내고, 이 클래스는 결과를 응답이나 예외로 번역하는 역할만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminLoginAttemptService loginAttemptService;
    private final AdminOtpService otpService;
    private final AdminUserRepository adminUserRepository;
    private final AdminJwtProvider jwtProvider;
    private final AuditLogService auditLogService;

    // ---------- 로그인 1단계 ----------

    public AdminLoginResponse login(AdminLoginRequest request, RequestInfo requestInfo) {
        AdminLoginAttemptService.Outcome outcome = loginAttemptService.attempt(
                request.loginId(), request.password(), requestInfo, otpService.required());

        switch (outcome.result()) {
            case BLOCKED -> throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_LOCKED,
                    "잠긴 계정 로그인 시도: " + request.loginId());
            case BAD_CREDENTIALS -> throw new BusinessException(ErrorCode.LOGIN_FAILED,
                    "자격 증명 불일치: " + request.loginId());
            case SUCCESS -> { /* 아래에서 다음 단계를 정한다 */ }
        }

        AdminUser admin = outcome.admin();
        if (admin.isOtpEnabled()) {
            return AdminLoginResponse.pending(AdminLoginResponse.Step.OTP_REQUIRED,
                    jwtProvider.issueOtpChallengeToken(admin));
        }
        if (otpService.required()) {
            return AdminLoginResponse.pending(AdminLoginResponse.Step.OTP_SETUP_REQUIRED,
                    jwtProvider.issueOtpSetupToken(admin));
        }
        return issueTokens(admin);
    }

    // ---------- 로그인 2단계 ----------

    public AdminLoginResponse verifyOtp(String stepToken, String code, RequestInfo requestInfo) {
        AdminUser admin = resolveStep(stepToken, AdminTokenType.OTP_CHALLENGE);
        AdminOtpService.Verification verification = otpService.verify(admin.getId(), code, requestInfo);

        switch (verification.result()) {
            case LOCKED -> throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_LOCKED,
                    "OTP 확인 중 잠긴 계정: " + admin.getLoginId());
            case BAD_CODE -> throw new BusinessException(ErrorCode.ADMIN_OTP_INVALID,
                    "OTP 불일치: " + admin.getLoginId());
            case TOTP, RECOVERY_CODE -> { /* 아래에서 토큰 발급 */ }
        }

        AdminLoginResponse response = issueTokens(reload(admin.getId()));
        return verification.result() == AdminOtpService.VerifyResult.RECOVERY_CODE
                ? response.withRecoveryCodesRemaining(verification.recoveryCodesRemaining())
                : response;
    }

    /**
     * OTP 등록 시작. 로그인 도중이면 2단계 표로, 이미 로그인한 관리자면 그 세션으로 부른다.
     */
    public AdminOtpSetupResponse beginOtpSetup(String stepToken, AdminPrincipal principal) {
        Long adminId = hasText(stepToken)
                ? resolveStep(stepToken, AdminTokenType.OTP_SETUP).getId()
                : requireSignedIn(principal);
        return AdminOtpSetupResponse.from(otpService.beginSetup(adminId));
    }

    /**
     * OTP 등록 완료. 로그인 도중이면 이 등록이 곧 2단계 통과라 토큰까지 함께 내준다.
     */
    public AdminOtpActivateResponse activateOtp(String stepToken, String code, AdminPrincipal principal,
                                                RequestInfo requestInfo) {
        boolean duringLogin = hasText(stepToken);
        Long adminId = duringLogin
                ? resolveStep(stepToken, AdminTokenType.OTP_SETUP).getId()
                : requireSignedIn(principal);

        List<String> recoveryCodes = otpService.activate(adminId, code, duringLogin, requestInfo);
        AdminLoginResponse login = duringLogin ? issueTokens(reload(adminId)) : null;
        return new AdminOtpActivateResponse(recoveryCodes, login);
    }

    public AdminOtpStatusResponse otpStatus(AdminPrincipal principal) {
        return AdminOtpStatusResponse.from(otpService.status(requireSignedIn(principal)));
    }

    public AdminOtpRecoveryCodesResponse regenerateRecoveryCodes(AdminPrincipal principal, String code,
                                                                 RequestInfo requestInfo) {
        return new AdminOtpRecoveryCodesResponse(
                otpService.regenerateRecoveryCodes(requireSignedIn(principal), code, requestInfo));
    }

    // ---------- 로그아웃 ----------

    /**
     * 로그아웃.
     *
     * <p>토큰 세대를 올려 <b>이미 발급된 토큰을 실제로 무효화</b>한다. 클라이언트가
     * 토큰을 버리는 것만으로는 로그아웃이 아니다 — 그 사이 복사된 토큰은 만료까지 살아 있다.
     */
    @Transactional
    public void logout(AdminPrincipal principal, RequestInfo requestInfo) {
        AdminUser admin = adminUserRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND,
                        "로그아웃 대상 계정 없음: " + principal.id()));

        admin.invalidateTokens();

        auditLogService.record(AuditRecord.builder(AuditActions.ADMIN_LOGOUT)
                .actor(ActorType.ADMIN, admin.getLoginId())
                .target(AuditActions.TARGET_ADMIN_USER, String.valueOf(admin.getId()))
                .after(Map.of("tokenVersion", admin.getTokenVersion()))
                .request(requestInfo.ip(), requestInfo.userAgent())
                .build());
    }

    // ---------- 토큰 재발급 ----------

    /**
     * Refresh 토큰으로 Access 토큰 재발급.
     *
     * <p>Refresh 토큰은 회전시키지 않는다. 회전하려면 토큰 세대를 올려야 하는데, 그러면
     * 같은 관리자가 다른 브라우저에서 켜 둔 세션이 함께 끊긴다. 관리자 Refresh 수명이
     * 8시간(하루 근무 미만)이라 회전 없이도 노출 창이 제한적이다.
     *
     * <p>OTP 가 필수인데 아직 등록하지 않은 계정은 재발급하지 않는다. OTP 를 켜기 전에 받아 둔
     * 토큰으로 계속 일하게 두면, 등록을 요구하는 의미가 그 토큰의 수명만큼 미뤄진다.
     */
    @Transactional(readOnly = true)
    public AdminLoginResponse refresh(String refreshToken, RequestInfo requestInfo) {
        AdminTokenClaims claims = jwtProvider.parse(refreshToken, AdminTokenType.REFRESH);

        AdminUser admin = adminUserRepository.findById(claims.adminId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        "존재하지 않는 계정의 리프레시 토큰: " + claims.adminId()));

        if (admin.getTokenVersion() != claims.tokenVersion()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "무효화된 리프레시 토큰: " + admin.getLoginId());
        }
        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_LOCKED,
                    "비활성 계정의 토큰 재발급 시도: " + admin.getLoginId());
        }
        if (otpService.required() && !admin.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "OTP 미등록 계정의 재발급 — 다시 로그인해 등록해야 한다: " + admin.getLoginId());
        }

        auditLogService.record(AuditRecord.builder(AuditActions.ADMIN_TOKEN_REFRESH)
                .actor(ActorType.ADMIN, admin.getLoginId())
                .target(AuditActions.TARGET_ADMIN_USER, String.valueOf(admin.getId()))
                .request(requestInfo.ip(), requestInfo.userAgent())
                .build());

        return AdminLoginResponse.of(
                jwtProvider.issueAccessToken(admin),
                refreshToken,
                jwtProvider.accessTtlSeconds(),
                AdminUserResponse.from(admin));
    }

    // ---------- 내 정보 ----------

    @Transactional(readOnly = true)
    public AdminUserResponse me(AdminPrincipal principal) {
        return adminUserRepository.findById(principal.id())
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND,
                        "계정 없음: " + principal.id()));
    }

    // ---------- 내부 ----------

    private AdminLoginResponse issueTokens(AdminUser admin) {
        return AdminLoginResponse.of(
                jwtProvider.issueAccessToken(admin),
                jwtProvider.issueRefreshToken(admin),
                jwtProvider.accessTtlSeconds(),
                AdminUserResponse.from(admin));
    }

    /**
     * 2단계 표를 확인한다.
     *
     * <p>만료·위조·종류 불일치를 하나의 오류로 돌려준다. 화면은 이 오류를 받으면 처음부터 다시
     * 로그인하게 한다 — 무엇이 틀렸는지 알려줄 이유가 없고, 할 수 있는 조치도 같다.
     */
    private AdminUser resolveStep(String stepToken, AdminTokenType type) {
        if (!hasText(stepToken)) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_STEP_EXPIRED, "2단계 표 없음");
        }
        AdminTokenClaims claims;
        try {
            claims = jwtProvider.parse(stepToken, type);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_STEP_EXPIRED,
                    "2단계 표 거부 (" + type + "): " + e.getMessage());
        }

        AdminUser admin = adminUserRepository.findById(claims.adminId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_OTP_STEP_EXPIRED,
                        "2단계 표의 계정 없음: " + claims.adminId()));
        // 표를 받은 뒤 잠기거나 OTP 가 초기화됐으면 세대가 올라가 있다.
        if (admin.getTokenVersion() != claims.tokenVersion()) {
            throw new BusinessException(ErrorCode.ADMIN_OTP_STEP_EXPIRED,
                    "무효화된 2단계 표: " + admin.getLoginId());
        }
        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_LOCKED,
                    "비활성 계정의 2단계 진행: " + admin.getLoginId());
        }
        return admin;
    }

    private AdminUser reload(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND, "계정 없음: " + adminId));
    }

    private static Long requireSignedIn(AdminPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "로그인 없이 OTP 관리 요청");
        }
        return principal.id();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
