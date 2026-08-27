package com.hanseongge.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 로그인 응답.
 *
 * <p>토큰을 응답 본문으로 내려주고 쿠키로 심지 않는다. 관리자 화면은 SPA 이고,
 * 쿠키로 실으면 CSRF 방어를 별도로 붙여야 한다 — 무상태 Bearer 방식이 관리자 API 에
 * 붙는 표면이 더 작다. 사용자(STEP 6)의 Refresh 쿠키와는 정책이 다르다.
 */
@Schema(name = "AdminLoginResponse", description = "관리자 로그인 결과")
public record AdminLoginResponse(

        @Schema(description = "Access 토큰 (Authorization: Bearer <token>)")
        String accessToken,

        @Schema(description = "Refresh 토큰")
        String refreshToken,

        @Schema(description = "토큰 유형", example = "Bearer")
        String tokenType,

        @Schema(description = "Access 토큰 만료까지 남은 초", example = "1800")
        long expiresIn,

        @Schema(description = "로그인한 관리자 정보")
        AdminUserResponse admin
) {

    public static AdminLoginResponse of(String accessToken, String refreshToken,
                                        long expiresIn, AdminUserResponse admin) {
        return new AdminLoginResponse(accessToken, refreshToken, "Bearer", expiresIn, admin);
    }
}
