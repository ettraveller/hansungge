package com.hanseongge.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 로그인 응답.
 *
 * <p>토큰을 응답 본문으로 내려주고 쿠키로 심지 않는다. 관리자 화면은 SPA 이고,
 * 쿠키로 실으면 CSRF 방어를 별도로 붙여야 한다 — 무상태 Bearer 방식이 관리자 API 에
 * 붙는 표면이 더 작다. 사용자(STEP 6)의 Refresh 쿠키와는 정책이 다르다.
 *
 * <h2>step</h2>
 * 비밀번호만 맞힌 단계에서는 토큰이 없고 {@code stepToken} 만 있다. 화면은 {@code step} 을 보고
 * 다음 입력(6자리 / 인증 앱 등록)을 띄운다.
 */
@Schema(name = "AdminLoginResponse", description = "관리자 로그인 결과")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminLoginResponse(

        @Schema(description = "로그인 진행 단계", example = "AUTHENTICATED")
        Step step,

        @Schema(description = "2단계 표. OTP_REQUIRED / OTP_SETUP_REQUIRED 일 때만 있다")
        String stepToken,

        @Schema(description = "Access 토큰 (Authorization: Bearer <token>)")
        String accessToken,

        @Schema(description = "Refresh 토큰")
        String refreshToken,

        @Schema(description = "토큰 유형", example = "Bearer")
        String tokenType,

        @Schema(description = "Access 토큰 만료까지 남은 초", example = "1800")
        Long expiresIn,

        @Schema(description = "로그인한 관리자 정보")
        AdminUserResponse admin,

        @Schema(description = "복구 코드로 들어왔을 때 남은 복구 코드 수")
        Long recoveryCodesRemaining
) {

    public enum Step {
        /** 로그인 완료 — 토큰이 들어 있다 */
        AUTHENTICATED,
        /** 인증 앱의 6자리를 넣어야 한다 */
        OTP_REQUIRED,
        /** 인증 앱을 먼저 등록해야 한다 */
        OTP_SETUP_REQUIRED
    }

    public static AdminLoginResponse of(String accessToken, String refreshToken,
                                        long expiresIn, AdminUserResponse admin) {
        return new AdminLoginResponse(Step.AUTHENTICATED, null, accessToken, refreshToken, "Bearer",
                expiresIn, admin, null);
    }

    public static AdminLoginResponse pending(Step step, String stepToken) {
        return new AdminLoginResponse(step, stepToken, null, null, null, null, null, null);
    }

    public AdminLoginResponse withRecoveryCodesRemaining(long remaining) {
        return new AdminLoginResponse(step, stepToken, accessToken, refreshToken, tokenType, expiresIn, admin,
                remaining);
    }
}
