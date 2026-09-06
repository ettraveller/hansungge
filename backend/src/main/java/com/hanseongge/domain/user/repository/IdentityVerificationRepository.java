package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {

    /** 같은 공급자 거래번호가 두 번 들어오는 것을 애플리케이션에서도 먼저 막는다. DB UNIQUE 가 최종 방어선이다. */
    Optional<IdentityVerification> findByProviderAndProviderTxId(String provider, String providerTxId);
}
