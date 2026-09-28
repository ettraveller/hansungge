package com.hanseongge.domain.payment.repository;

import com.hanseongge.domain.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
