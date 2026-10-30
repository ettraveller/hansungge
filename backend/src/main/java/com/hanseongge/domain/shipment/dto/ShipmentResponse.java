package com.hanseongge.domain.shipment.dto;

import com.hanseongge.domain.shipment.entity.Carrier;
import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 배송 정보. 관리자·고객이 같은 모양으로 받는다.
 *
 * <p>송장번호를 가리지 않는다. 고객은 자기 주문만 볼 수 있고(주문번호로 조회할 때
 * 소유자를 확인한다), 그 번호가 있어야 택배사에서 조회할 수 있다.
 *
 * <p>{@link #trackingUrl} 에 송장번호를 붙이지 않는다 — 우체국은 번호를 GET 으로 받는
 * 공개 주소를 보장하지 않아서, 붙인 링크가 어느 날 조용히 빈 화면이 된다.
 * 조회 페이지로 보내고 번호는 화면에서 복사하게 한다.
 */
@Schema(name = "ShipmentResponse", description = "배송 정보")
public record ShipmentResponse(

        @Schema(description = "배송 상태", example = "SHIPPED") ShipmentStatus status,
        @Schema(description = "상태 문구", example = "발송") String statusLabel,

        @Schema(description = "택배사 코드. 아직 발송 전이면 null", example = "KOREA_POST")
        Carrier carrier,
        @Schema(description = "택배사 이름", example = "우체국택배") String carrierName,
        @Schema(description = "택배사 조회 페이지 주소") String trackingUrl,

        @Schema(description = "송장번호(숫자만). 발송 전이면 null", example = "6012345678901")
        String trackingNo,

        @Schema(description = "발송 시각") OffsetDateTime shippedAt,
        @Schema(description = "수령 시각") OffsetDateTime deliveredAt,

        @Schema(description = "배송조회를 마지막으로 부른 시각. null 이면 조회가 꺼져 있거나 아직 부른 적 없다")
        OffsetDateTime trackingSyncedAt,
        @Schema(description = "택배사가 준 상태 문자열 원본") String trackingStatus,
        @Schema(description = "진행 단계(JSON). 조회가 꺼져 있으면 null") String trackingDetail
) {

    /** 배송 행이 아직 없는 주문. 화면이 널 검사를 하지 않도록 "발송 대기"로 채워 준다. */
    public static ShipmentResponse pending() {
        return new ShipmentResponse(
                ShipmentStatus.PENDING, ShipmentStatus.PENDING.label(),
                null, null, null, null, null, null, null, null, null);
    }

    public static ShipmentResponse from(Shipment s) {
        Carrier carrier = s.getCarrier();
        return new ShipmentResponse(
                s.getStatus(),
                s.getStatus().label(),
                carrier,
                carrier == null ? null : carrier.displayName(),
                carrier == null ? null : carrier.trackingUrl(),
                s.getTrackingNo(),
                s.getShippedAt(),
                s.getDeliveredAt(),
                s.getTrackingSyncedAt(),
                s.getTrackingStatus(),
                s.getTrackingDetail());
    }
}
