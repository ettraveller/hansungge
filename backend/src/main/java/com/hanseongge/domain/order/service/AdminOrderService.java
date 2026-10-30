package com.hanseongge.domain.order.service;

import com.hanseongge.domain.order.dto.AdminOrderSearchCondition;
import com.hanseongge.domain.order.dto.AdminOrderSummaryResponse;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.repository.OrderItemRepository;
import com.hanseongge.domain.order.repository.OrderRepository;
import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.service.ShipmentService;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 주문 조회.
 *
 * <p>{@link OrderService} 와 나눠 둔다. 그쪽은 <b>고객이</b> 주문을 만들고 자기 것을
 * 보는 흐름이고, 소유자 검사가 모든 길목에 박혀 있다. 관리자는 남의 주문을 보는 것이
 * 일이라 그 검사가 없다 — 한 클래스에 두면 "이 메서드는 소유자를 보는가"를 매번
 * 확인해야 하고, 한 번 빠뜨리면 고객이 남의 주문을 보게 된다.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentService shipmentService;

    /**
     * 주문 목록.
     *
     * <p>품목과 배송을 <b>한 번에 묶어 읽는다.</b> 줄마다 따로 부르면 20줄짜리 목록이
     * 41번 질의가 된다 — 그 비용이 목록 화면에서 바로 드러난다.
     */
    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryResponse> search(AdminOrderSearchCondition condition,
                                                  Pageable pageable) {
        Page<Order> orders = orderRepository.searchForAdmin(condition, pageable);
        if (orders.isEmpty()) {
            return orders.map(order -> AdminOrderSummaryResponse.of(order, List.of(), null));
        }

        List<Long> ids = orders.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = itemsOf(ids);
        Map<Long, Shipment> shipments = shipmentService.findByOrderIds(ids);

        return orders.map(order -> AdminOrderSummaryResponse.of(
                order,
                itemsByOrder.getOrDefault(order.getId(), List.of()),
                shipments.get(order.getId())));
    }

    /** 목록 상단의 "발송 대기 N건". 판정은 저장소 한 곳에 있다. */
    @Transactional(readOnly = true)
    public long countAwaitingShipment() {
        return orderRepository.countAwaitingShipment();
    }

    /**
     * 주문 상세.
     *
     * <p>고객이 보는 것과 <b>같은 모양</b>({@link OrderResponse})으로 준다. 관리자용을
     * 따로 만들면 금액 계산이 둘로 갈라지고, 그때 어느 쪽이 맞는지 알 수 없다.
     * 관리자에게만 필요한 배송은 별도 호출로 가져간다.
     */
    @Transactional(readOnly = true)
    public OrderResponse detail(Long orderId) {
        Order order = requireOrder(orderId);
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        return OrderResponse.of(order, items);
    }

    @Transactional(readOnly = true)
    public Order requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND,
                        "주문 없음: " + orderId));
    }

    private Map<Long, List<OrderItem>> itemsOf(Collection<Long> orderIds) {
        return orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }
}
