package com.hanseongge.domain.user.controller;

import com.hanseongge.domain.user.dto.AuthProvidersResponse;
import com.hanseongge.domain.user.dto.EmailVerificationRequest;
import com.hanseongge.domain.user.dto.IdentityCompleteRequest;
import com.hanseongge.domain.user.dto.IdentityResultResponse;
import com.hanseongge.domain.user.dto.IdentityStartRequest;
import com.hanseongge.domain.user.dto.IdentityStartResponse;
import com.hanseongge.domain.user.dto.OAuthAuthorizeResponse;
import com.hanseongge.domain.user.dto.OAuthCallbackRequest;
import com.hanseongge.domain.user.dto.PasswordResetGrantResponse;
import com.hanseongge.domain.user.dto.PasswordResetRequest;
import com.hanseongge.domain.user.dto.PasswordSignInRequest;
import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.SignUpCompleteRequest;
import com.hanseongge.domain.user.dto.SignUpStartRequest;
import com.hanseongge.domain.user.dto.SignUpTokenResponse;
import com.hanseongge.domain.user.dto.SmsLoginRequest;
import com.hanseongge.domain.user.dto.SmsSendRequest;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.service.UserAuthService;
import com.hanseongge.domain.user.service.UserRefreshTokenService;
import com.hanseongge.global.auth.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 회원 인증 API (개발계획 §7).
 *
 * <p>전 구간 비인증 접근이 허용된다 — 로그인하려는 사람은 아직 토큰이 없다.
 * 자격 증명은 각각 인가코드·진행 토큰·Refresh 쿠키이며 서비스가 검증한다.
 *
 * <p>Refresh 토큰은 응답 본문에 담지 않는다. HttpOnly 쿠키로만 오간다.
 */
@Tag(name = "회원 인증", description = "휴대폰·소셜 로그인 / 가입 / 비밀번호 찾기 / 토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserAuthService userAuthService;

    @Operation(summary = "로그인 화면 구성",
            description = "쓸 수 있는 소셜 제공자, 가입 시 받을 약관, 가입 가능 여부, 인증번호 로그인 가능 여부. "
                    + "화면에 하드코딩하지 않는다.")
    @GetMapping("/providers")
    public ResponseEntity<AuthProvidersResponse> providers() {
        return ResponseEntity.ok(userAuthService.describeProviders());
    }

    // -----------------------------------------------------------------
    // 로그인
    // -----------------------------------------------------------------

    @Operation(summary = "휴대폰번호 로그인",
            description = "실패 사유를 세분해 알려주지 않는다 — 가입 여부를 확인하는 데 쓰인다. "
                    + "다만 비밀번호가 없는 계정과 잠긴 계정은 알려준다. 다음에 할 일이 다르다.")
    @PostMapping("/login")
    public ResponseEntity<SignInResponse> signInWithPassword(
            @Valid @RequestBody PasswordSignInRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(userAuthService.signInWithPassword(
                request.phone(), request.password(), response));
    }

    @Operation(summary = "인증번호 로그인 — 문자 요청",
            description = "가입 여부와 무관하게 같은 응답을 준다. 문자는 회원에게만 실제로 나간다. "
                    + "번호당 60초에 1통, 1시간에 5통.")
    @PostMapping("/sms/send")
    public ResponseEntity<Void> sendLoginCode(@Valid @RequestBody SmsSendRequest request,
                                              HttpServletRequest httpRequest) {
        userAuthService.sendLoginCode(request.phone(), RequestInfo.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "인증번호 로그인",
            description = "문자로 받은 6자리. 5분 유효, 5회까지, 성공 즉시 폐기.")
    @PostMapping("/sms/login")
    public ResponseEntity<SignInResponse> signInWithCode(@Valid @RequestBody SmsLoginRequest request,
                                                         HttpServletResponse response) {
        return ResponseEntity.ok(userAuthService.signInWithCode(request.phone(), request.code(), response));
    }

    @Operation(summary = "소셜 로그인 시작",
            description = "제공자 로그인 URL 과 state 를 돌려준다. state 는 브라우저에 보관했다가 "
                    + "콜백에서 대조한다 (인가코드 주입 방지).")
    @PostMapping("/oauth/{provider}/authorize")
    public ResponseEntity<OAuthAuthorizeResponse> authorize(@PathVariable OAuthProviderType provider) {
        return ResponseEntity.ok(userAuthService.authorize(provider));
    }

    @Operation(summary = "소셜 로그인 콜백",
            description = "인가코드를 교환해 로그인시킨다. 처음 오는 소셜 계정이면 "
                    + "signUpRequired=true 와 함께 약관 목록을 돌려주고, <b>계정은 아직 만들지 않는다</b>.")
    @PostMapping("/oauth/{provider}/callback")
    public ResponseEntity<SignInResponse> callback(@PathVariable OAuthProviderType provider,
                                                   @Valid @RequestBody OAuthCallbackRequest request,
                                                   HttpServletResponse response) {
        return ResponseEntity.ok(
                userAuthService.callback(provider, request.code(), request.state(), response));
    }

    // -----------------------------------------------------------------
    // 가입 — 자체: 약관 → 간편인증 → 비밀번호·이메일 / 소셜: 약관
    // -----------------------------------------------------------------

    @Operation(summary = "자체 가입 시작 (약관 동의)",
            description = "간편인증(개인정보 수집)보다 먼저 받는다. 본인인증 공급자가 없으면 503 SIGNUP_UNAVAILABLE.")
    @PostMapping("/signup/start")
    public ResponseEntity<SignUpTokenResponse> startSignUp(@Valid @RequestBody SignUpStartRequest request) {
        return ResponseEntity.ok(new SignUpTokenResponse(userAuthService.startSignUp(request.agreements())));
    }

    @Operation(summary = "소셜 가입 (약관 동의로 완료)",
            description = "콜백에서 받은 토큰과 약관 동의. 본인인증 없이 계정을 만들고 로그인시킨다. "
                    + "만 14세 확인은 필수 약관 동의로 갈음한다.")
    @PostMapping("/signup/social")
    public ResponseEntity<SignInResponse> signUpSocial(@Valid @RequestBody SignUpStartRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        return ResponseEntity.ok(userAuthService.signUpSocial(
                request.signUpToken(), request.agreements(), RequestInfo.from(httpRequest), response));
    }

    @Operation(summary = "간편인증 시작 (자체 가입)",
            description = "인증창을 여는 폼을 준다. 화면은 hidden 필드로 담아 그대로 전송한다.")
    @PostMapping("/identity/start")
    public ResponseEntity<IdentityStartResponse> startIdentity(
            @Valid @RequestBody IdentityStartRequest request) {
        return ResponseEntity.ok(userAuthService.startIdentityVerification(request.signUpToken()));
    }

    /**
     * 본인확인 공급자의 결과 수신.
     *
     * <p>이니시스는 결과를 브라우저 폼 POST 로 보낸다. 화면(Next.js 페이지)은 POST 를 받을 수 없어
     * 여기서 받아 303 으로 화면에 넘긴다. 여기서 신원을 조회하지 않는다 — 어느 흐름(가입·비밀번호
     * 찾기)의 결과인지는 화면이 들고 있는 진행 토큰이 안다.
     */
    @Operation(summary = "본인확인 결과 수신 (공급자 → 백엔드)",
            description = "공급자가 호출한다. 받은 값을 짧게 만료되는 토큰으로 묶어 화면 주소로 보낸다.")
    @RequestMapping(value = "/identity/return", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Void> identityReturn(@RequestParam Map<String, String> returned) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, userAuthService.identityReturnRedirect(returned))
                .build();
    }

    @Operation(summary = "간편인증 결과 제출 (자체 가입)",
            description = "서버가 공급자에 되물어 검증한다. 다음 단계(step)를 돌려준다 — "
                    + "SET_PASSWORD · EXISTING_ACCOUNT. CI 를 주지 않는 수단이면 400 IDENTITY_KEY_UNAVAILABLE.")
    @PostMapping("/identity/complete")
    public ResponseEntity<IdentityResultResponse> completeIdentity(
            @Valid @RequestBody IdentityCompleteRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userAuthService.completeIdentityVerification(
                request.token(), request.providerToken(), RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "가입 중 이메일 인증번호 발송",
            description = "선택 이메일. 본인확인을 마친 진행 토큰이 필요하다.")
    @PostMapping("/email/verification")
    public ResponseEntity<Void> sendEmailCode(@Valid @RequestBody EmailVerificationRequest request,
                                              HttpServletRequest httpRequest) {
        userAuthService.sendSignUpEmailCode(request.signUpToken(), request.email(),
                RequestInfo.from(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "자체 가입 완료",
            description = "비밀번호와 선택 이메일. 이름·생년월일·휴대폰은 본인확인 값을 그대로 쓴다. "
                    + "회원·본인확인 연결·동의이력이 한 트랜잭션으로 생성되고 자동 로그인된다.")
    @PostMapping("/signup/complete")
    public ResponseEntity<SignInResponse> completeSignUp(@Valid @RequestBody SignUpCompleteRequest request,
                                                         HttpServletRequest httpRequest,
                                                         HttpServletResponse response) {
        return ResponseEntity.ok(userAuthService.completeSignUp(
                request, RequestInfo.from(httpRequest), response));
    }

    // -----------------------------------------------------------------
    // 비밀번호 찾기 — 본인확인 → 새 비밀번호
    // -----------------------------------------------------------------

    @Operation(summary = "비밀번호 찾기 — 본인확인 시작")
    @PostMapping("/password/reset/identity/start")
    public ResponseEntity<IdentityStartResponse> startPasswordReset() {
        return ResponseEntity.ok(userAuthService.startPasswordReset());
    }

    @Operation(summary = "비밀번호 찾기 — 본인확인 결과 제출",
            description = "본인확인한 신원으로 가입된 계정을 찾아 5분짜리 재설정 토큰을 준다.")
    @PostMapping("/password/reset/identity/complete")
    public ResponseEntity<PasswordResetGrantResponse> completePasswordResetIdentity(
            @Valid @RequestBody IdentityCompleteRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userAuthService.completePasswordResetIdentity(
                request.token(), request.providerToken(), RequestInfo.from(httpRequest)));
    }

    @Operation(summary = "새 비밀번호 설정",
            description = "성공하면 <b>그 회원의 다른 세션을 전부 끊고</b> 잠금도 푼다. "
                    + "임시 비밀번호를 문자로 보내지 않는다.")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userAuthService.resetPassword(request.resetToken(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------
    // 세션
    // -----------------------------------------------------------------

    @Operation(summary = "Access 토큰 재발급",
            description = "Refresh 쿠키로 재발급한다. Refresh 토큰도 함께 회전하므로 "
                    + "이전 토큰은 즉시 무효가 된다.")
    @PostMapping("/refresh")
    public ResponseEntity<SignInResponse> refresh(
            @CookieValue(name = UserRefreshTokenService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        return ResponseEntity.ok(userAuthService.refresh(refreshToken, response));
    }

    @Operation(summary = "로그아웃",
            description = "이 기기의 세션만 끊는다. 서버에 기록이 없어도 쿠키는 반드시 지운다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = UserRefreshTokenService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        userAuthService.logout(refreshToken, response);
        return ResponseEntity.noContent().build();
    }
}
