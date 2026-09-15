package com.hanseongge.domain.admin.controller;

import com.hanseongge.domain.admin.dto.AdminCreateRequest;
import com.hanseongge.domain.admin.dto.AdminOtpResetRequest;
import com.hanseongge.domain.admin.dto.AdminPasswordResetRequest;
import com.hanseongge.domain.admin.dto.AdminStatusChangeRequest;
import com.hanseongge.domain.admin.dto.AdminUpdateRequest;
import com.hanseongge.domain.admin.dto.AdminUserResponse;
import com.hanseongge.domain.admin.service.AdminUserService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 계정 관리 API (설계서 §5 — {@code CRUD /api/admin/admins}).
 *
 * <p><b>클래스 전체가 SUPER_ADMIN 전용이다.</b> 메서드마다 붙이지 않고 클래스에 한 번만
 * 선언하는 이유: 나중에 엔드포인트를 추가하는 사람이 어노테이션을 빠뜨려도 열리지 않는다.
 * 권한은 "붙이면 막힌다"보다 "안 붙여도 막혀 있다"가 안전하다.
 *
 * <p>DELETE 를 두지 않는다. 관리자 계정을 지우면 그 계정이 남긴 감사로그의 주체가
 * 사라진다. 퇴사·권한 회수는 {@code PATCH /{id}/status} 로 {@code DISABLED} 처리한다.
 */
@Tag(name = "관리자 계정 관리", description = "관리자 계정 CRUD (SUPER_ADMIN 전용)")
@RestController
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "관리자 계정 목록")
    @GetMapping
    public ResponseEntity<PageResponse<AdminUserResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.list(pageable));
    }

    @Operation(summary = "관리자 계정 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.get(id));
    }

    @Operation(summary = "관리자 계정 생성")
    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody AdminCreateRequest request) {
        AdminUserResponse created = adminUserService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "관리자 계정 수정 (이름·권한)",
            description = "본인 계정의 권한은 변경할 수 없다. 마지막 활성 SUPER_ADMIN 도 강등할 수 없다.")
    @PutMapping("/{id}")
    public ResponseEntity<AdminUserResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody AdminUpdateRequest request,
                                                    @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(adminUserService.update(id, request, actor));
    }

    @Operation(summary = "관리자 계정 상태 변경",
            description = "잠금 해제(ACTIVE)·정지(DISABLED). 사유가 필수이며 감사로그에 남는다.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> changeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody AdminStatusChangeRequest request,
                                                          @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(adminUserService.changeStatus(id, request, actor));
    }

    @Operation(summary = "관리자 비밀번호 초기화",
            description = "잠긴 계정이면 잠금도 함께 해제한다. 대상의 기존 토큰은 전부 무효가 된다.")
    @PatchMapping("/{id}/password")
    public ResponseEntity<AdminUserResponse> resetPassword(@PathVariable Long id,
                                                           @Valid @RequestBody AdminPasswordResetRequest request,
                                                           @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(adminUserService.resetPassword(id, request, actor));
    }

    @Operation(summary = "관리자 OTP 초기화",
            description = "휴대폰 분실·교체 때 쓴다. 대상은 다음 로그인에서 인증 앱을 다시 등록한다. "
                    + "잠긴 계정이면 잠금도 해제하고, 기존 토큰과 복구 코드는 전부 무효가 된다. 본인 계정은 할 수 없다.")
    @PatchMapping("/{id}/otp-reset")
    public ResponseEntity<AdminUserResponse> resetOtp(@PathVariable Long id,
                                                      @Valid @RequestBody AdminOtpResetRequest request,
                                                      @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(adminUserService.resetOtp(id, request, actor));
    }
}
