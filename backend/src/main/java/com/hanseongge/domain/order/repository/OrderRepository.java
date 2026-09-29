package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Page<Order> findByUserIdOrderByOrderedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);

    /**
     * 결제 없이 방치된 주문.
     *
     * <p>{@code SKIP LOCKED} 를 쓰지 않는다 — 지금은 단일 인스턴스라 스케줄러가 하나뿐이고,
     * 접는 동작 자체가 멱등이다(이미 취소된 주문은 다시 읽어 걸러낸다). 인스턴스를 늘릴 때
     * 다시 볼 것.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status IN :statuses AND o.orderedAt < :cutoff
            ORDER BY o.id ASC
            """)
    List<Order> findStale(@Param("statuses") Collection<OrderStatus> statuses,
                          @Param("cutoff") OffsetDateTime cutoff,
                          Limit limit);

    /** 주문번호 채번용 — 오늘 만든 주문 수. {@code idx_order_user} 가 아니라 order_no 접두로 센다. */
    long countByOrderNoStartingWith(String prefix);
}
