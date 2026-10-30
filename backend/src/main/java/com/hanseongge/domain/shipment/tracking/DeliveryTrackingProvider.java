package com.hanseongge.domain.shipment.tracking;

import com.hanseongge.domain.shipment.entity.Carrier;

/**
 * 택배사 배송조회 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에).
 *
 * <h2>없어도 돌아간다</h2>
 * 이 기능은 <b>있으면 좋은 것</b>이지 배송의 전제가 아니다. 키가 없으면 고객은
 * 송장번호와 택배사 조회 링크로 확인하고, 관리자는 발송·수령을 손으로 기록한다.
 * 그래서 구현이 없을 때 예외를 던지지 않고 {@link #isConfigured()} 로 알린다 —
 * 키가 없다는 이유로 주문 화면이 깨지면 안 된다.
 *
 * <h2>수령 확정은 여기서 하지 않는다</h2>
 * 택배사가 "배송완료"라고 해도 그것으로 {@code deliveredAt} 을 쓰지 않는다.
 * 고가 귀금속의 수령 확인은 사람이 남기는 기록이어야 분쟁에서 근거가 된다
 * (이용약관 제10조). 이 인터페이스는 진행 단계를 가져다줄 뿐이다.
 */
public interface DeliveryTrackingProvider {

    /** 쓸 수 있는 상태인가. 키가 없으면 false 이고, 호출하는 쪽은 조용히 넘어간다. */
    boolean isConfigured();

    /**
     * 진행 단계를 가져온다.
     *
     * @throws DeliveryTrackingException 택배사가 응답하지 않거나 번호를 모를 때.
     *         이것은 <b>기술적 실패</b>다 — 호출하는 쪽이 잡아서 다음 주기에 다시 묻는다
     */
    TrackingResult track(Carrier carrier, String trackingNo);
}
