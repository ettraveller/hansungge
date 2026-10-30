package com.hanseongge.domain.shipment.entity;

/**
 * 배송 상태. V32 의 {@code chk_shipment_status} 와 값이 일치해야 한다.
 *
 * <p>주문 상태({@code OrderStatus})와 따로 둔다. 주문은 결제·취소·환불까지 아우르는
 * 흐름이고, 이쪽은 물건이 어디쯤 가 있는가만 말한다. 섞으면 "결제는 됐는데 아직
 * 안 보냈다"와 "보냈는데 아직 도착 전"을 한 값으로 표현하려다 어긋난다.
 */
public enum ShipmentStatus {

    /** 송장번호가 아직 없다. 발송 대기다. */
    PENDING("발송 대기"),

    /** 송장번호를 넣고 보냈다. 택배사가 아직 아무것도 알려주지 않았을 수 있다. */
    SHIPPED("발송"),

    /** 택배사가 이동 중이라고 알려 왔다. 배송조회 API 가 켜져 있을 때만 쓰인다. */
    IN_TRANSIT("배송 중"),

    /** 수령. 고가 귀금속이라 이 기록이 분쟁의 근거가 된다. */
    DELIVERED("배송 완료");

    private final String label;

    ShipmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 배송조회를 더 물어볼 이유가 있는가. 수령이 끝났으면 그만 묻는다. */
    public boolean isTrackable() {
        return this == SHIPPED || this == IN_TRANSIT;
    }
}
