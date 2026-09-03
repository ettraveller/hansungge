package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /**
     * 로그인 조회. <b>이메일이 아니라 제공자 식별자로 찾는다</b> (개발계획 §7.2).
     * 이메일은 바뀔 수 있고, 바뀌는 값을 계정 키로 쓰면 계정 탈취 경로가 된다.
     */
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProviderType provider,
                                                            String providerUserId);

    List<SocialAccount> findByUserIdOrderByConnectedAtAsc(Long userId);

    long countByUserId(Long userId);
}
