package com.hanseongge.domain.user.controller;

import com.hanseongge.domain.user.dto.AuthProvidersResponse;
import com.hanseongge.domain.user.dto.OAuthAuthorizeResponse;
import com.hanseongge.domain.user.dto.OAuthCallbackRequest;
import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.SignUpRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 인증 API (개발계획 §7).
 *
 * <p>전 구간 비인증 접근이 허용된다 — 로그인하려는 사람은 아직 토큰이 없다.
 * 자격 증명은 각각 인가코드·가입토큰·Refresh 쿠키이며 서비스가 검증한다.
 *
 * <p>Refresh 토큰은 응답 본문에 담지 않는다. HttpOnly 쿠키로만 오간다.
 */
@Tag(name = "회원 인증", description = "소셜 로그인 / 가입 / 토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserAuthService userAuthService;

    @Operation(summary = "로그인 화면 구성",
            description = "지금 사용할 수 있는 소셜 제공자와 가입 시 받을 약관 목록. "
                    + "제공자 목록은 서버 설정에 따라 달라진다 — 화면에 하드코딩하지 않는다.")
    @GetMapping("/providers")
    public ResponseEntity<AuthProvidersResponse> providers() {
        return ResponseEntity.ok(userAuthService.describeProviders());
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
                    + "signUpRequired=true 와 함께 가입 토큰·약관 목록을 돌려주고, "
                    + "<b>계정은 아직 만들지 않는다</b> — 약관 동의 전이기 때문이다.")
    @PostMapping("/oauth/{provider}/callback")
    public ResponseEntity<SignInResponse> callback(@PathVariable OAuthProviderType provider,
                                                   @Valid @RequestBody OAuthCallbackRequest request,
                                                   HttpServletResponse response) {
        return ResponseEntity.ok(
                userAuthService.callback(provider, request.code(), request.state(), response));
    }

    @Operation(summary = "가입 완료",
            description = "필수 약관(이용약관·개인정보·만14세)에 모두 동의해야 계정이 만들어진다. "
                    + "회원·소셜연동·동의이력이 한 트랜잭션으로 생성된다.")
    @PostMapping("/signup")
    public ResponseEntity<SignInResponse> signUp(@Valid @RequestBody SignUpRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse response) {
        return ResponseEntity.ok(
                userAuthService.signUp(request, RequestInfo.from(httpRequest), response));
    }

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
