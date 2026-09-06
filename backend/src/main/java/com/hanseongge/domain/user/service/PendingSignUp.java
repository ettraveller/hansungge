package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.oauth.OAuthUserInfo;

/**
 * 가입이 진행 중인 상태 — 아직 계정은 없다.
 *
 * <p>이 값이 서명된 {@code signUpToken} 에 담겨 브라우저를 왕복한다. 계정을 먼저 만들고
 * 약관을 나중에 받으면 그 사이 동의 없이 개인정보를 보관한 상태가 되므로, 동의가 끝나는
 * 순간까지 아무것도 만들지 않는다.
 *
 * <h2>⚠️ 본인확인 결과를 여기 담지 않는다</h2>
 * <b>JWT 는 서명될 뿐 암호화되지 않는다.</b> 이름·생년월일·성별·DI 를 claim 에 넣으면
 * 브라우저에서 Base64 디코딩만으로 전부 보이고, 그 값이 sessionStorage 에 남는다.
 * 그래서 신원은 DB({@code identity_verifications})에 두고 여기에는
 * {@link #identityVerificationId} 만 담는다.
 *
 * <p>소셜 제공자가 준 이메일은 담는다. 그것은 사용자가 방금 제공자 화면에서 직접 고른
 * 값이고 우리 응답으로 이미 한 번 나갔던 값이라, 새로 노출되는 것이 없다.
 *
 * @param social                 소셜 가입일 때만. 자체 가입은 null
 * @param email                  가입할 주소
 * @param emailVerificationId    자체 가입일 때만. 소셜은 제공자가 이미 검증했다
 * @param identityVerificationId 본인확인 전이면 null, 마쳤으면 그 행의 id
 */
public record PendingSignUp(
        OAuthUserInfo social,
        String email,
        Long emailVerificationId,
        Long identityVerificationId) {

    public boolean isSocial() {
        return social != null;
    }

    public boolean isIdentityVerified() {
        return identityVerificationId != null;
    }

    /** 본인확인을 마친 뒤 그 결과를 붙인 새 상태. 기존 값은 그대로 둔다. */
    public PendingSignUp withIdentity(Long identityVerificationId) {
        return new PendingSignUp(social, email, emailVerificationId, identityVerificationId);
    }

    public SignUpMaterial toMaterial(String rawPassword) {
        return new SignUpMaterial(email, emailVerificationId, identityVerificationId, social, rawPassword);
    }
}
