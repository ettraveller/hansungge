package com.hanseongge.domain.shipment.tracking;

/**
 * 배송조회 실패.
 *
 * <p>기술적 실패다 — 택배사가 응답하지 않거나, 아직 번호를 모르거나, 키가 거부됐다.
 * 주문이나 배송 자체에는 아무 문제가 없다. 호출하는 쪽은 이것을 잡아 조용히 넘기고
 * 다음 주기에 다시 묻는다.
 */
public class DeliveryTrackingException extends RuntimeException {

    public DeliveryTrackingException(String message) {
        super(message);
    }

    public DeliveryTrackingException(String message, Throwable cause) {
        super(message, cause);
    }
}
