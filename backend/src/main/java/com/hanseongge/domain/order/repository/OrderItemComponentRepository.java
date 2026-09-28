package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.entity.OrderItemComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemComponentRepository extends JpaRepository<OrderItemComponent, Long> {

    List<OrderItemComponent> findByOrderItemIdOrderBySortAscIdAsc(Long orderItemId);
}
