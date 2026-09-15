package com.hanseongge.domain.admin.controller;

import com.hanseongge.domain.admin.dto.AdminLoginRequest;
import com.hanseongge.domain.admin.dto.AdminLoginResponse;
import com.hanseongge.domain.admin.dto.AdminOtpActivateResponse;
import com.hanseongge.domain.admin.dto.AdminOtpCodeRequest;
import com.hanseongge.domain.admin.dto.AdminOtpRecoveryCodesResponse;
import com.hanseongge.domain.admin.dto.AdminOtpSetupResponse;
import com.hanseongge.domain.admin.dto.AdminOtpStatusResponse;
import com.hanseongge.domain.admin.dto.AdminOtpStepRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 인증 API (설계서 §5, §10).
 *
 * <p>컨트롤러는 DTO 입출력·검증·권한만 맡는다. 계산과 상태 변경은 전부 서비스에 있다
 * (코딩규칙 P4).
 *
 * <h2>2단계 표는 본문으로 받는다</h2>
 * 로그인 도중의 OTP 호출은 {@code Authorization} 헤더가 아니라 본문의 {@code stepToken} 으로
 * 받는다. 헤더에 실으면 관리자 인증 필터가 그 표를 Access 토큰으로 검사하다 거절한다 —
 * 2단계 표로 관리자 API 를 부를 수 없게 한 바로 그 장치다.
 */
@Tag(name = "관리자 인증", description = "관리자 로그인 / OTP / 로그아웃 / 토큰")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminUserService adminUserService;

    @Operation(summary = "관리자 로그인 (1단계)",
            description = "비밀번호가 맞으면 step 으로 다음 단계를 알려준다. "
                    + "OTP_REQUIRED 면 /otp/verify, OTP_SETUP_REQUIRED 면 /otp/setup → /otp/activate 로 이어간다. "
                    + "OTP 가 꺼진 운영에서만 바로 토큰(AUTHENTICATED)이 나온다. "
                    + "연속 5회 실패하면 계정이 잠긴다. IP당 분당 10회로 제한된다.")
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminAuthService.login(request, RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "OTP 확인 (로그인 2단계)",
            description = "인증 앱의 6자리 또는 복구 코드를 받는다. 맞으면 토큰을 발급한다. "
                    + "같은 6자리는 두 번 쓸 수 없다. 연속 5회 틀리면 계정이 잠긴다.")
    @PostMapping("/otp/verify")
    public ResponseEntity<AdminLoginResponse> verifyOtp(@Valid @RequestBody AdminOtpCodeRequest request,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminAuthService.verifyOtp(
                request.stepToken(), request.code(), RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "OTP 등록 시작",
            description = "인증 앱에 넣을 키와 otpauth 주소를 돌려준다. 첫 로그인이면 본문에 stepToken 을, "
                    + "이미 로그인한 관리자면 Authorization 헤더로 부른다. 다시 부르면 키가 바뀐다.")
    @PostMapping("/otp/setup")
    public ResponseEntity<AdminOtpSetupResponse> beginOtpSetup(
            @RequestBody(required = false) AdminOtpStepRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.ok(adminAuthService.beginOtpSetup(
                request == null ? null : request.stepToken(), principal));
    }

    @Operation(summary = "OTP 등록 완료",
            description = "앱에 뜬 6자리로 등록을 확인하고 복구 코드 10개를 돌려준다(이 응답에서만 볼 수 있다). "
                    + "첫 로그인 도중이면 login 에 토큰이 함께 들어 있다.")
    @PostMapping("/otp/activate")
    public ResponseEntity<AdminOtpActivateResponse> activateOtp(
            @Valid @RequestBody AdminOtpCodeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminAuthService.activateOtp(
                request.stepToken(), request.code(), principal, RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "내 OTP 상태", description = "등록 여부·등록일·남은 복구 코드 수.")
    @GetMapping("/otp")
    public ResponseEntity<AdminOtpStatusResponse> otpStatus(@AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.ok(adminAuthService.otpStatus(principal));
    }

    @Operation(summary = "복구 코드 재발급",
            description = "지금 인증 앱의 6자리를 확인한 뒤 새 복구 코드 10개를 돌려준다. 옛 코드는 전부 무효가 된다.")
    @PostMapping("/otp/recovery-codes")
    public ResponseEntity<AdminOtpRecoveryCodesResponse> regenerateRecoveryCodes(
            @Valid @RequestBody AdminOtpCodeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminAuthService.regenerateRecoveryCodes(
                principal, request.code(), RequestInfo.from(httpRequest)));
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
