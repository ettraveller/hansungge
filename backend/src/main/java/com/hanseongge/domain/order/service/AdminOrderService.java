package com.hanseongge.domain.order.service;

import com.hanseongge.domain.order.dto.AdminOrderSearchCondition;
import com.hanseongge.domain.order.dto.AdminOrderSummaryResponse;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.entity.OrderStatus;
import com.hanseongge.domain.order.repository.OrderItemRepository;
import com.hanseongge.domain.order.repository.OrderRepository;
import com.hanseongge.domain.payment.entity.Payment;
import com.hanseongge.domain.payment.repository.PaymentRepository;
import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.service.ShipmentService;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentService shipmentService;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

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

    /**
     * 무통장 입금을 확인했다 — 사람이 통장을 보고 누른다.
     *
     * <h2>금액을 받지 않는다</h2>
     * 관리자 화면이 보낸 금액을 주문 금액과 맞춰 봐야 자기 자신과 비교하는 꼴이다.
     * 실제 입금액 대조는 통장에서 사람이 하고, 이 버튼은 "주문 금액대로 들어왔다"는
     * <b>선언</b>이다. 덜 들어온 입금은 결제 완료로 넘길 일이 아니라 연락할 일이다.
     *
     * <h2>결제 기록을 남긴다</h2>
     * {@code payments} 에 행을 만든다. PG 를 거치지 않아 거래번호가 없지만, 남기지
     * 않으면 결제 완료된 주문 중 무통장 건만 결제 이력이 비어 보인다 — 나중에
     * "이 주문 돈 받았나"를 주문 상태 하나로만 판단해야 한다.
     *
     * <h2>장바구니를 비운다</h2>
     * 카드 승인과 같은 자리다. 주문서를 만들 때가 아니라 <b>돈이 들어온 뒤</b> 비운다.
     *
     * @throws BusinessException 무통장 주문이 아니거나 이미 결제가 끝난 주문일 때
     */
    @Transactional
    public OrderResponse confirmDeposit(Long orderId, String actor) {
        Order order = requireOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            // 두 사람이 같은 주문을 동시에 확인했다. 두 번째는 조용히 지금 상태를 돌려준다.
            return detail(orderId);
        }

        order.confirmDeposit();

        Payment payment = paymentRepository.save(
                Payment.ready(order.getId(), "BANK_TRANSFER", order.getTotalAmount()));
        // PG 거래번호가 없다 — 입금을 확인한 사람이 거래의 근거다.
        payment.approve(null, "BANK_TRANSFER", order.getTotalAmount(), OffsetDateTime.now());

        log.info("[ORDER] 무통장 입금 확인 orderNo={} amount={} depositor={} actor={}",
                order.getOrderNo(), order.getTotalAmount(), order.getDepositorName(), actor);

        // 실패해도 입금 확인을 되돌리지 않는다 — 장바구니가 남는 것보다
        // 받은 돈이 미결제로 남는 것이 훨씬 나쁘다.
        try {
            orderService.clearOrdered(order.getUserId(), orderService.itemsOf(order.getId()));
        } catch (RuntimeException e) {
            log.warn("[ORDER] 입금 확인 후 장바구니를 비우지 못했습니다. orderNo={}",
                    order.getOrderNo(), e);
        }
        return detail(orderId);
    }

    private Map<Long, List<OrderItem>> itemsOf(Collection<Long> orderIds) {
        return orderItemRepository.findByOrderIdInOrderByIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }
}
