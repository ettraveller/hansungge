package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.dto.AdminOrderSearchCondition;
import com.hanseongge.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {

    /** 관리자 주문 목록. 조건은 전부 선택이고, 없으면 전체를 최신순으로 준다. */
    Page<Order> searchForAdmin(AdminOrderSearchCondition condition, Pageable pageable);

    /** 발송 대기 건수. 목록 상단에 늘 보여 준다. */
    long countAwaitingShipment();
}
