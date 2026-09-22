package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.IdentityPurpose;
import com.hanseongge.domain.user.entity.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    /** 같은 공급자 거래번호가 두 번 들어오는 것을 애플리케이션에서도 먼저 막는다. DB UNIQUE 가 최종 방어선이다. */
    Optional<IdentityVerification> findByProviderAndProviderTxId(String provider, String providerTxId);

    /** 이 회원이 {@code since} 이후에 그 목적의 본인인증을 마쳤는가 (소진 시각 기준). */
    boolean existsByUserIdAndPurposeAndConsumedAtAfter(Long userId, IdentityPurpose purpose, OffsetDateTime since);
}
