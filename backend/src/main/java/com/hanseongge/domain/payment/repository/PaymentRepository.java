package com.hanseongge.domain.payment.repository;

import com.hanseongge.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findByPgTid(String pgTid);
}
