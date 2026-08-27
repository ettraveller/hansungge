package com.hanseongge.domain.user.controller;

import com.hanseongge.domain.user.dto.AgreementStatusResponse;
import com.hanseongge.domain.user.dto.AgreementUpdateRequest;
import com.hanseongge.domain.user.dto.OAuthCallbackRequest;
import com.hanseongge.domain.user.dto.ProfileUpdateRequest;
import com.hanseongge.domain.user.dto.SocialAccountResponse;
import com.hanseongge.domain.user.dto.UserResponse;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.service.OAuthTokenFactory;
import com.hanseongge.domain.user.service.UserAuthService;
import com.hanseongge.domain.user.service.UserProfileService;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 마이페이지 API. 전 구간 로그인 필요.
 */
@Tag(name = "마이페이지", description = "내 정보 / 약관 / 소셜 연동 / 탈퇴")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;
    private final UserAuthService authService;
    private final OAuthTokenFactory tokenFactory;

    @Operation(summary = "내 정보")
    @GetMapping
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.me(principal.id()));
    }

    @Operation(summary = "내 정보 수정")
    @PatchMapping
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(principal.id(), request));
    }

    @Operation(summary = "약관 동의 현황",
            description = "약관별 가장 최근 상태. 이력은 append-only 라 과거 동의도 모두 남아 있다.")
    @GetMapping("/agreements")
    public ResponseEntity<List<AgreementStatusResponse>> agreements(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.agreements(principal.id()));
    }

    @Operation(summary = "선택 약관 동의/철회",
            description = "필수 약관은 철회할 수 없다 — 그것은 동의 철회가 아니라 탈퇴다. "
                    + "기존 기록을 고치지 않고 새 행을 추가한다.")
    @PatchMapping("/agreements")
    public ResponseEntity<List<AgreementStatusResponse>> changeAgreement(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AgreementUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(profileService.changeAgreement(
                principal.id(), request.type(), request.agreed(), RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "연동된 소셜 계정")
    @GetMapping("/social-accounts")
    public ResponseEntity<List<SocialAccountResponse>> socialAccounts(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.socialAccounts(principal.id()));
    }

    @Operation(summary = "소셜 계정 추가 연동",
            description = "로그인한 상태에서 본인이 직접 연결한다 (설계서 §7.2). "
                    + "이메일이 같다는 이유로 서버가 계정을 자동 병합하지 않는 이유이기도 하다.")
    @PostMapping("/social-accounts/{provider}/link")
    public ResponseEntity<List<SocialAccountResponse>> link(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable OAuthProviderType provider,
            @Valid @RequestBody OAuthCallbackRequest request) {
        tokenFactory.verifyState(request.state(), provider);
        OAuthUserInfo info = authService.exchange(provider, request.code(), request.state());
        return ResponseEntity.ok(profileService.link(
                principal.id(), info.provider(), info.providerUserId(), info.email()));
    }

    @Operation(summary = "소셜 연동 해제",
            description = "마지막 하나는 해제할 수 없다 — 자체 아이디/비밀번호가 없어 "
                    + "로그인 수단이 사라진다.")
    @DeleteMapping("/social-accounts/{provider}")
    public ResponseEntity<List<SocialAccountResponse>> disconnect(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable OAuthProviderType provider) {
        return ResponseEntity.ok(profileService.disconnect(principal.id(), provider));
    }

    @Operation(summary = "회원 탈퇴",
            description = "회원 행을 지우지 않고 상태만 WITHDRAWN 으로 바꾼다 — 주문·결제 이력이 "
                    + "회원을 참조하고, 거래 기록은 법정 보존 대상이다. 모든 기기의 세션이 끊긴다.")
    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                         HttpServletResponse response) {
        profileService.withdraw(principal.id());
        // 탈퇴 직후 브라우저에 남은 Refresh 쿠키까지 지운다.
        authService.logout(null, response);
        return ResponseEntity.noContent().build();
    }
}
