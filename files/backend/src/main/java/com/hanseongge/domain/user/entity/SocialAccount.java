package com.hanseongge.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 소셜 연동 ({@code social_accounts}).
 *
 * <p>로그인 조회 키는 <b>{@code (provider, provider_user_id)}</b> 하나뿐이다. 이메일로
 * 찾지 않는다 — 제공자가 주는 이메일은 바뀔 수 있고, 바뀌는 값을 계정 식별자로 쓰면
 * 이메일을 갈아탄 공격자가 남의 계정에 들어간다. {@code ux_social} UNIQUE 제약이
 * 같은 소셜 계정이 두 회원에 붙는 것을 DB 수준에서 막는다.
 */
@Entity
@Getter
@Table(name = "social_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private OAuthProviderType provider;

    /** 제공자가 발급한 불변 식별자. 이메일과 달리 바뀌지 않는다. */
    @Column(name = "provider_user_id", length = 255, nullable = false)
    private String providerUserId;

    /** 연동 시점 이메일. 표시·안내용이며 조회 키가 아니다. */
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "connected_at", nullable = false)
    private OffsetDateTime connectedAt;

    private SocialAccount(Long userId, OAuthProviderType provider, String providerUserId, String email) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.connectedAt = OffsetDateTime.now();
    }

    public static SocialAccount connect(Long userId, OAuthProviderType provider,
                                        String providerUserId, String email) {
        return new SocialAccount(userId, provider, providerUserId, email);
    }
}
