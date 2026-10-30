package com.hanseongge.domain.shipment.repository;

import com.hanseongge.domain.shipment.entity.Carrier;
import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.entity.ShipmentStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    List<Shipment> findByOrderIdIn(Collection<Long> orderIds);

    /** 같은 송장번호가 다른 주문에 이미 쓰였는가. */
    Optional<Shipment> findByCarrierAndTrackingNo(Carrier carrier, String trackingNo);

    /**
     * 배송조회를 다시 물어볼 건.
     *
     * <p>아직 가는 중인 것만 고른다 — 수령이 끝난 건을 계속 물으면 호출 수만 늘고
     * 답은 바뀌지 않는다. 한 번도 안 물어본 건({@code trackingSyncedAt IS NULL})도
     * 포함되도록 조건을 나눠 쓴다.
     */
    List<Shipment> findByStatusInAndTrackingSyncedAtIsNull(
            Collection<ShipmentStatus> statuses, Limit limit);

    List<Shipment> findByStatusInAndTrackingSyncedAtBefore(
            Collection<ShipmentStatus> statuses, OffsetDateTime before, Limit limit);
}
