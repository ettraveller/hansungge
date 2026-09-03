package com.hanseongge.domain.admin.service;

import com.hanseongge.domain.admin.dto.AdminLoginRequest;
import com.hanseongge.domain.admin.dto.AdminLoginResponse;
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

import java.util.Map;

/**
 * 관리자 인증 (설계서 §10).
 *
 * <p><b>클래스 레벨 {@code @Transactional} 을 두지 않는다.</b> 로그인 실패 경로는
 * "DB 를 변경하고 나서 예외를 던져야" 하는데, 하나의 트랜잭션으로 감싸면 그 변경이
 * 롤백된다. 실제 DB 변경은 {@link AdminLoginAttemptService} 안에서 끝내고,
 * 이 클래스는 결과를 응답이나 예외로 번역하는 역할만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminLoginAttemptService loginAttemptService;
    private final AdminUserRepository adminUserRepository;
    private final AdminJwtProvider jwtProvider;
    private final AuditLogService auditLogService;

    // ---------- 로그인 ----------

    public AdminLoginResponse login(AdminLoginRequest request, RequestInfo requestInfo) {
        AdminLoginAttemptService.Outcome outcome =
                loginAttemptService.attempt(request.loginId(), request.password(), requestInfo);

        switch (outcome.result()) {
            case BLOCKED -> throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_LOCKED,
                    "잠긴 계정 로그인 시도: " + request.loginId());
            case BAD_CREDENTIALS -> throw new BusinessException(ErrorCode.LOGIN_FAILED,
                    "자격 증명 불일치: " + request.loginId());
            case SUCCESS -> { /* 아래에서 토큰 발급 */ }
        }

        AdminUser admin = outcome.admin();
        return AdminLoginResponse.of(
                jwtProvider.issueAccessToken(admin),
                jwtProvider.issueRefreshToken(admin),
                jwtProvider.accessTtlSeconds(),
                AdminUserResponse.from(admin));
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
}
