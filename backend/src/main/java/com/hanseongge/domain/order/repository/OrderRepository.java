package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Page<Order> findByUserIdOrderByOrderedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatusIn(Long userId, java.util.Collection<com.hanseongge.domain.order.entity.OrderStatus> statuses);

    /** 주문번호 채번용 — 오늘 만든 주문 수. {@code idx_order_user} 가 아니라 order_no 접두로 센다. */
    long countByOrderNoStartingWith(String prefix);
}
