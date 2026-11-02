package com.hanseongge.domain.policy.repository;

import com.hanseongge.domain.policy.entity.RefundPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

    /** 현재 판 = 가장 최근에 발행된 판. V34 시드가 있어 운영에서 비어 있을 수 없다. */
    Optional<RefundPolicy> findTopByOrderByIdDesc();

    Page<RefundPolicy> findAllByOrderByIdDesc(Pageable pageable);
}
