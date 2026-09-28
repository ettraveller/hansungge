package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    /** 목록 화면에서 주문마다 한 번씩 조회하지 않기 위해 (N+1). */
    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);
}
