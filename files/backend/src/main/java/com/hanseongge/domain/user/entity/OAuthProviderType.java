package com.hanseongge.domain.user.entity;

/**
 * 소셜 로그인 제공자. V1 {@code social_accounts.provider} CHECK 제약과 값이 일치해야 한다.
 *
 * <p>자체 아이디/비밀번호 회원가입은 만들지 않는다 (개발계획 §7.1) — 비밀번호를 보관하지
 * 않으면 유출될 비밀번호도 없다.
 */
public enum OAuthProviderType {

    GOOGLE("구글"),
    NAVER("네이버"),
    KAKAO("카카오");

    private final String displayName;

    OAuthProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
