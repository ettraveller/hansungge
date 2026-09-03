package com.hanseongge.domain.user.oauth;

import com.hanseongge.domain.user.entity.OAuthProviderType;

/**
 * 소셜 제공자에서 받아온 최소 정보.
 *
 * <p>필요한 것만 담는다. 제공자가 주는 프로필 사진·생일·성별 따위를 함께 들여오면
 * 쓰지도 않을 개인정보를 보관하게 되고, 그만큼 유출 시 피해가 커진다.
 *
 * @param provider       어느 제공자인가
 * @param providerUserId 제공자가 발급한 <b>불변</b> 식별자. 계정 조회의 유일한 키다
 * @param email          이메일. 제공자가 주지 않거나 사용자가 동의하지 않으면 null 이다
 * @param name           표시 이름. 마찬가지로 null 일 수 있다
 */
public record OAuthUserInfo(
        OAuthProviderType provider,
        String providerUserId,
        String email,
        String name
) {
    public OAuthUserInfo {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId 가 없으면 계정을 식별할 수 없습니다");
        }
    }
}
