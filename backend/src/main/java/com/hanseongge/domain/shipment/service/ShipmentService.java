package com.hanseongge.domain.shipment.service;

import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.repository.OrderRepository;
import com.hanseongge.domain.shipment.dto.ShipmentResponse;
import com.hanseongge.domain.shipment.entity.Carrier;
import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.repository.ShipmentRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 배송 — 송장 입력, 발송, 수령 기록.
 *
 * <h2>주문 상태와 배송 상태를 함께 움직인다</h2>
 * 송장을 넣으면 배송은 {@code SHIPPED} 가 되고 주문도 {@code SHIPPED} 가 된다.
 * 둘을 따로 두면 "주문은 준비 중인데 배송은 발송됨" 같은 상태가 생기고, 고객 화면과
 * 관리자 화면이 서로 다른 말을 한다. 한 트랜잭션에서 같이 바꾼다.
 *
 * <h2>배송 행은 결제 후에 만들지 않는다</h2>
 * 주문마다 미리 만들어 두면 결제도 안 된 주문의 배송 행이 쌓인다. 송장을 처음 넣는
 * 순간 만든다 — 그래서 {@link #findOrCreate} 가 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Optional<Shipment> find(Long orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }

    /**
     * 고객이 자기 주문의 배송을 본다.
     *
     * <p>소유자 검사를 <b>이 메서드 안에서</b> 한다. 주문번호만 알면 조회되게 두면
     * 번호 규칙(HS+날짜+순번)이 단순해서 숫자를 바꿔 가며 남의 송장을 긁어 갈 수 있다.
     * 없는 주문과 남의 주문을 똑같이 404 로 답한다 ({@code Order#requireOwnedBy}).
     *
     * <p>배송 행이 없으면 "발송 대기"를 채워 준다 — 화면이 널 검사를 하지 않게.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse mine(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.requireOwnedBy(userId);

        return shipmentRepository.findByOrderId(order.getId())
                .map(ShipmentResponse::from)
                .orElseGet(ShipmentResponse::pending);
    }

    /** 주문 목록 화면이 여러 건의 배송을 한 번에 읽는다. N+1 을 피하려고 묶어서 준다. */
    @Transactional(readOnly = true)
    public Map<Long, Shipment> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return shipmentRepository.findByOrderIdIn(orderIds).stream()
                .collect(java.util.stream.Collectors.toMap(Shipment::getOrderId, s -> s));
    }

    /**
     * 송장번호를 넣고 발송 처리한다.
     *
     * <p>같은 송장번호가 다른 주문에 이미 쓰였으면 거절한다. 그대로 두면 한쪽 고객이
     * 남의 배송을 조회하게 되고, 분실 다툼이 생겼을 때 어느 주문의 물건인지 가릴 수 없다.
     * DB 에도 UNIQUE 가 있지만(V32) 여기서 먼저 걸러야 사유를 말해 줄 수 있다.
     */
    @Transactional
    @AuditLog(action = AuditActions.ORDER_SHIP,
              targetType = AuditActions.TARGET_ORDER,
              targetId = "#orderId",
              after = "{'carrier': #carrier, 'trackingNo': #trackingNo}")
    public Shipment ship(Long orderId, Carrier carrier, String trackingNo, String adminLoginId) {
        Order order = requireOrder(orderId);
        Shipment shipment = findOrCreate(orderId);

        requireTrackingNoFree(carrier, trackingNo, orderId);

        shipment.ship(carrier, trackingNo);
        order.markShipped();

        log.info("[SHIPMENT] 발송 orderId={} orderNo={} carrier={} by={}",
                orderId, order.getOrderNo(), carrier, adminLoginId);
        return shipment;
    }

    /**
     * 수령 기록.
     *
     * <p>고가 귀금속이라 이 기록이 분실·수령거부를 다툴 때 근거가 된다 (이용약관 제10조).
     * 그래서 택배사 응답이 아니라 <b>사람이</b> 남긴다.
     */
    @Transactional
    @AuditLog(action = AuditActions.ORDER_DELIVER,
              targetType = AuditActions.TARGET_ORDER,
              targetId = "#orderId")
    public Shipment markDelivered(Long orderId, String adminLoginId) {
        Order order = requireOrder(orderId);
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPMENT_NOT_SHIPPED));

        shipment.markDelivered();
        order.markDelivered();

        log.info("[SHIPMENT] 수령 orderId={} orderNo={} by={}",
                orderId, order.getOrderNo(), adminLoginId);
        return shipment;
    }

    /** 결제 완료 주문을 상품 준비 중으로. 배송 행은 아직 만들지 않는다. */
    @Transactional
    @AuditLog(action = AuditActions.ORDER_PREPARE,
              targetType = AuditActions.TARGET_ORDER,
              targetId = "#orderId")
    public Order markPreparing(Long orderId, String adminLoginId) {
        Order order = requireOrder(orderId);
        order.markPreparing();
        log.info("[SHIPMENT] 준비중 orderId={} orderNo={} by={}",
                orderId, order.getOrderNo(), adminLoginId);
        return order;
    }

    // =================================================================
    // 내부
    // =================================================================

    private Order requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND,
                        "주문 없음: " + orderId));
    }

    private Shipment findOrCreate(Long orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseGet(() -> shipmentRepository.save(Shipment.pending(orderId)));
    }

    /**
     * 그 송장번호가 다른 주문에 쓰이고 있지 않은가.
     *
     * <p>같은 주문에 다시 넣는 것은 괜찮다 — 번호를 고치다 원래 값으로 되돌리는 경우다.
     */
    private void requireTrackingNoFree(Carrier carrier, String rawTrackingNo, Long orderId) {
        String digits = rawTrackingNo == null ? null : rawTrackingNo.replaceAll("[^0-9]", "");
        if (digits == null || digits.isBlank()) {
            return;   // 자릿수 검사는 엔티티가 한다. 여기서는 중복만 본다
        }
        shipmentRepository.findByCarrierAndTrackingNo(carrier, digits)
                .filter(other -> !other.getOrderId().equals(orderId))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.SHIPMENT_TRACKING_NO_DUPLICATED,
                            "이미 orderId=" + other.getOrderId() + " 에 쓰인 번호");
                });
    }

    /** 배송조회를 다시 물어볼 건. 동기화 작업이 쓴다. */
    @Transactional(readOnly = true)
    public List<Shipment> findDueForTracking(java.time.OffsetDateTime staleBefore, int limit) {
        var trackable = List.of(
                com.hanseongge.domain.shipment.entity.ShipmentStatus.SHIPPED,
                com.hanseongge.domain.shipment.entity.ShipmentStatus.IN_TRANSIT);
        var never = shipmentRepository.findByStatusInAndTrackingSyncedAtIsNull(
                trackable, org.springframework.data.domain.Limit.of(limit));
        if (never.size() >= limit) {
            return never;
        }
        var stale = shipmentRepository.findByStatusInAndTrackingSyncedAtBefore(
                trackable, staleBefore, org.springframework.data.domain.Limit.of(limit - never.size()));
        return java.util.stream.Stream.concat(never.stream(), stale.stream()).toList();
    }
}
