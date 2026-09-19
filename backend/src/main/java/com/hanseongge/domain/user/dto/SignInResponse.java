package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 로그인 결과.
 *
 * <p>두 가지 결말을 한 타입으로 표현한다.
 * <ul>
 *   <li>로그인 완료 → {@code signUpRequired=false}, {@code accessToken} 발급 완료</li>
 *   <li>처음 오는 소셜 계정 → {@code signUpRequired=true}, 토큰 대신 {@code signUpToken} 과 약관 목록.
 *       다음은 약관 동의 → 본인확인이다</li>
 * </ul>
 *
 * <p>신규일 때 계정을 <b>아직 만들지 않았다.</b> 필수 약관 동의 전에 개인정보를 저장하면
 * 동의 없는 수집이 된다.
 *
 * <p>Refresh 토큰은 이 본문에 없다 — HttpOnly 쿠키로 나간다. 자바스크립트가 읽을 수 있는
 * 곳에 두면 XSS 한 번에 14일짜리 세션이 넘어간다.
 */
@Schema(name = "SignInResponse", description = "로그인 결과")
public record SignInResponse(

        @Schema(description = "약관 동의·본인확인을 거쳐 가입을 마쳐야 하는가", example = "false")
        boolean signUpRequired,

        @Schema(description = "Access 토큰. 가입이 필요하면 null")
        String accessToken,

        @Schema(description = "Access 토큰 만료까지 남은 초", example = "1800")
        Long expiresIn,

        @Schema(description = "로그인한 회원. 가입이 필요하면 null")
        UserResponse user,

        @Schema(description = "가입 진행 토큰. 로그인 완료면 null")
        String signUpToken,

        @Schema(description = "가입 시 동의받을 약관. 로그인 완료면 null")
        List<AuthProvidersResponse.AgreementItem> agreements,

        @Schema(description = "소셜에서 받아온 이메일. 제공자가 주지 않았으면 null")
        String suggestedEmail
) {

    public static SignInResponse signedIn(String accessToken, long expiresIn, UserResponse user) {
        return new SignInResponse(false, accessToken, expiresIn, user, null, null, null);
    }

    public static SignInResponse signUpRequired(String signUpToken,
                                                List<AuthProvidersResponse.AgreementItem> agreements,
                                                String suggestedEmail) {
        return new SignInResponse(true, null, null, null, signUpToken, agreements, suggestedEmail);
    }
}
