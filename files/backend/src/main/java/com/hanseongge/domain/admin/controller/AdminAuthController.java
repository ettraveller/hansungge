package com.hanseongge.domain.admin.controller;

import com.hanseongge.domain.admin.dto.AdminLoginRequest;
import com.hanseongge.domain.admin.dto.AdminLoginResponse;
import com.hanseongge.domain.admin.dto.AdminPasswordChangeRequest;
import com.hanseongge.domain.admin.dto.AdminTokenRefreshRequest;
import com.hanseongge.domain.admin.dto.AdminUserResponse;
import com.hanseongge.domain.admin.service.AdminAuthService;
import com.hanseongge.domain.admin.service.AdminUserService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.auth.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자 인증 API (설계서 §5, §10).
 *
 * <p>컨트롤러는 DTO 입출력·검증·권한만 맡는다. 계산과 상태 변경은 전부 서비스에 있다
 * (코딩규칙 P4).
 */
@Tag(name = "관리자 인증", description = "관리자 로그인 / 로그아웃 / 토큰")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminUserService adminUserService;

    @Operation(summary = "관리자 로그인",
            description = "성공 시 audience=admin JWT 를 발급한다. 연속 5회 실패하면 계정이 잠긴다. "
                    + "IP당 분당 10회로 제한된다.")
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminAuthService.login(request, RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "관리자 로그아웃",
            description = "토큰 세대를 올려 이미 발급된 토큰을 즉시 무효화한다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal principal,
                                       HttpServletRequest httpRequest) {
        adminAuthService.logout(principal, RequestInfo.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Access 토큰 재발급",
            description = "Refresh 토큰으로 Access 토큰을 재발급한다. Refresh 토큰 자체는 회전하지 않는다.")
    @PostMapping("/refresh")
    public ResponseEntity<AdminLoginResponse> refresh(@Valid @RequestBody AdminTokenRefreshRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminAuthService.refresh(request.refreshToken(), RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<AdminUserResponse> me(@AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.ok(adminAuthService.me(principal));
    }

    @Operation(summary = "내 비밀번호 변경",
            description = "현재 비밀번호 확인 후 변경한다. 성공하면 본인의 기존 토큰이 모두 무효가 된다.")
    @PatchMapping("/password")
    public ResponseEntity<Void> changeMyPassword(@AuthenticationPrincipal AdminPrincipal principal,
                                                 @Valid @RequestBody AdminPasswordChangeRequest request) {
        adminUserService.changeMyPassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
