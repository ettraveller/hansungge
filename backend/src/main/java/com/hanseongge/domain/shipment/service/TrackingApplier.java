package com.hanseongge.domain.shipment.service;

import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 결과를 한 건씩 저장한다.
 *
 * <h2>왜 별도 빈인가</h2>
 * 같은 클래스 안에서 부른 {@code @Transactional} 은 프록시를 타지 않아 트랜잭션이
 * 열리지 않는다 ({@code StaleOrderExpirer} 와 같은 이유).
 *
 * <h2>건마다 트랜잭션을 연다</h2>
 * 배치 전체를 한 트랜잭션에 묶으면 마지막 한 건이 실패할 때 앞서 성공한 것까지
 * 함께 롤백된다 — 조회는 이미 다 했는데 저장만 날아가고, 다음 주기에 같은 호출을
 * 또 하게 된다. 하루 호출 한도가 있는 API 라 그 낭비가 바로 비용이다.
 */
@Component
@RequiredArgsConstructor
public class TrackingApplier {

    private final ShipmentRepository shipmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(Long shipmentId, String externalStatus, String detailJson, boolean inTransit) {
        Shipment shipment = shipmentRepository.findById(shipmentId).orElse(null);
        if (shipment == null) {
            return;   // 그 사이 지워졌다. 다음 주기에 대상에서 빠진다
        }
        shipment.applyTracking(externalStatus, detailJson, inTransit);
    }
}
