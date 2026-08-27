package com.hanseongge.domain.admin.service;

import com.hanseongge.domain.admin.dto.AdminCreateRequest;
import com.hanseongge.domain.admin.dto.AdminPasswordChangeRequest;
import com.hanseongge.domain.admin.dto.AdminPasswordResetRequest;
import com.hanseongge.domain.admin.dto.AdminStatusChangeRequest;
import com.hanseongge.domain.admin.dto.AdminUpdateRequest;
import com.hanseongge.domain.admin.dto.AdminUserResponse;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 계정 관리 (설계서 §5 관리자 API, SUPER_ADMIN 전용).
 *
 * <h2>두 가지 자물쇠</h2>
 * <ol>
 *   <li><b>본인 계정의 권한·상태는 못 바꾼다.</b> 실수로 자기를 OPERATOR 로 낮추거나
 *       정지시키면 되돌릴 권한도 함께 사라진다.</li>
 *   <li><b>마지막 활성 SUPER_ADMIN 은 강등·정지되지 않는다.</b> 그 순간 아무도 관리자
 *       계정을 만들 수 없게 되고, 복구하려면 DB 를 직접 고쳐야 한다.</li>
 * </ol>
 * 두 규칙 모두 "권한이 있으면 할 수 있다"와는 다른 층위다. 권한 검사(@PreAuthorize)를
 * 통과한 요청이라도 시스템을 복구 불능으로 만드는 조작은 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(Pageable pageable) {
        Page<AdminUser> page = adminUserRepository.findAll(pageable);
        return PageResponse.of(page, AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long id) {
        return AdminUserResponse.from(find(id));
    }

    // ---------- 생성 ----------

    @Transactional
    @AuditLog(action = AuditActions.ADMIN_CREATE,
              targetType = AuditActions.TARGET_ADMIN_USER,
              targetId = "#result.id()")
    public AdminUserResponse create(AdminCreateRequest request) {
        if (adminUserRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_ID_DUPLICATED,
                    "중복 loginId: " + request.loginId());
        }

        AdminUser admin = AdminUser.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.role());

        AdminUser saved = adminUserRepository.save(admin);
        log.info("[ADMIN] 계정 생성 loginId={} role={}", saved.getLoginId(), saved.getRole());
        return AdminUserResponse.from(saved);
    }

    // ---------- 수정 ----------

    @Transactional
    @AuditLog(action = AuditActions.ADMIN_UPDATE,
              targetType = AuditActions.TARGET_ADMIN_USER,
              targetId = "#id")
    public AdminUserResponse update(Long id, AdminUpdateRequest request, AdminPrincipal actor) {
        AdminUser admin = find(id);
        AuditContext.before(snapshot(admin));

        if (admin.getRole() != request.role()) {
            denySelf(admin, actor, "본인 권한 변경");
            guardLastSuperAdmin(admin, request.role(), admin.getStatus());
            admin.changeRole(request.role());
        }
        admin.changeName(request.name());

        return AdminUserResponse.from(admin);
    }

    @Transactional
    @AuditLog(action = AuditActions.ADMIN_STATUS_CHANGE,
              targetType = AuditActions.TARGET_ADMIN_USER,
              targetId = "#id",
              after = "{'status': #request.status().name(), 'reason': #request.reason()}")
    public AdminUserResponse changeStatus(Long id, AdminStatusChangeRequest request, AdminPrincipal actor) {
        AdminUser admin = find(id);
        AuditContext.before(snapshot(admin));

        denySelf(admin, actor, "본인 상태 변경");
        guardLastSuperAdmin(admin, admin.getRole(), request.status());

        admin.changeStatus(request.status());
        log.info("[ADMIN] 상태 변경 loginId={} status={} reason={} by={}",
                admin.getLoginId(), request.status(), request.reason(), actor.loginId());
        return AdminUserResponse.from(admin);
    }

    // ---------- 비밀번호 ----------

    /**
     * 타인 비밀번호 초기화 (SUPER_ADMIN 전용).
     *
     * <p>초기화하면 잠금도 함께 푼다. 비밀번호를 새로 줬는데 계정이 잠겨 있으면
     * 운영자가 두 번 요청해야 하고, 그 사이 "왜 안 되지"로 시간이 샌다.
     */
    @Transactional
    @AuditLog(action = AuditActions.ADMIN_PASSWORD_RESET,
              targetType = AuditActions.TARGET_ADMIN_USER,
              targetId = "#id",
              after = "{'reason': #request.reason()}")
    public AdminUserResponse resetPassword(Long id, AdminPasswordResetRequest request, AdminPrincipal actor) {
        AdminUser admin = find(id);
        AuditContext.before(snapshot(admin));

        admin.changePassword(passwordEncoder.encode(request.newPassword()));
        if (admin.isLocked()) {
            admin.changeStatus(AdminStatus.ACTIVE);
        }

        log.info("[ADMIN] 비밀번호 초기화 loginId={} by={}", admin.getLoginId(), actor.loginId());
        return AdminUserResponse.from(admin);
    }

    /** 본인 비밀번호 변경. 성공하면 자신의 기존 토큰도 전부 무효가 된다. */
    @Transactional
    @AuditLog(action = AuditActions.ADMIN_PASSWORD_CHANGE,
              targetType = AuditActions.TARGET_ADMIN_USER,
              targetId = "#actor.id()",
              after = "null")
    public void changeMyPassword(AdminPrincipal actor, AdminPasswordChangeRequest request) {
        AdminUser admin = find(actor.id());

        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_MISMATCH,
                    "현재 비밀번호 불일치: " + admin.getLoginId());
        }
        if (passwordEncoder.matches(request.newPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "기존과 동일한 비밀번호: " + admin.getLoginId());
        }

        admin.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("[ADMIN] 비밀번호 변경 loginId={}", admin.getLoginId());
    }

    // ---------- 내부 ----------

    private AdminUser find(Long id) {
        return adminUserRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND, "관리자 없음: " + id));
    }

    private void denySelf(AdminUser target, AdminPrincipal actor, String what) {
        if (actor != null && target.getId().equals(actor.id())) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_MODIFICATION_DENIED,
                    what + " 시도: " + actor.loginId());
        }
    }

    /**
     * 변경 후에도 활성 SUPER_ADMIN 이 최소 1명 남는지 확인한다.
     *
     * @param nextRole   변경 후 권한
     * @param nextStatus 변경 후 상태
     */
    private void guardLastSuperAdmin(AdminUser target, AdminRole nextRole, AdminStatus nextStatus) {
        boolean wasActiveSuper = target.getRole() == AdminRole.SUPER_ADMIN && target.isActive();
        boolean staysActiveSuper = nextRole == AdminRole.SUPER_ADMIN && nextStatus == AdminStatus.ACTIVE;

        if (!wasActiveSuper || staysActiveSuper) {
            return;
        }
        long activeSupers = adminUserRepository.countByRoleAndStatus(AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
        if (activeSupers <= 1) {
            throw new BusinessException(ErrorCode.ADMIN_LAST_SUPER_ADMIN,
                    "마지막 활성 SUPER_ADMIN 변경 시도: " + target.getLoginId());
        }
    }

    /** 감사로그용 변경 전 스냅샷. 엔티티가 아니라 값 사본을 남긴다. */
    private Map<String, Object> snapshot(AdminUser admin) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("loginId", admin.getLoginId());
        values.put("name", admin.getName());
        values.put("role", admin.getRole().name());
        values.put("status", admin.getStatus().name());
        values.put("loginFailCount", admin.getLoginFailCount());
        return values;
    }
}
