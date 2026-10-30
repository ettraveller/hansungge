package com.hanseongge.domain.order.dto;

import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.entity.OrderStatus;
import com.hanseongge.domain.shipment.dto.ShipmentResponse;
import com.hanseongge.domain.shipment.entity.Shipment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 주문 목록의 한 줄.
 *
 * <p>{@link OrderResponse} 를 쓰지 않는다. 목록은 수십 건을 한 번에 그리는데 그쪽은
 * 항목·주소·요청사항까지 다 담아서, 화면에 안 쓰는 값을 매 줄마다 실어 나르게 된다.
 *
 * <p>대신 목록이 실제로 보여주는 것만 담는다 — 주문번호, 시각, 받는 분, 대표 상품명,
 * 금액, 상태, <b>송장번호</b>. 송장번호가 비어 있는 줄이 곧 발송 대기다.
 */
@Schema(name = "AdminOrderSummaryResponse", description = "관리자 주문 목록 한 줄")
public record AdminOrderSummaryResponse(

        @Schema(description = "주문 id") Long id,
        @Schema(description = "주문번호", example = "HS20261030-000003") String orderNo,
        @Schema(description = "주문 일시") OffsetDateTime orderedAt,

        @Schema(description = "상태 코드", example = "PAID") OrderStatus status,
        @Schema(description = "상태 문구", example = "결제 완료") String statusLabel,

        @Schema(description = "받는 사람") String receiverName,
        @Schema(description = "대표 상품명", example = "순금 골드바 3.75g 외 1건") String orderName,
        @Schema(description = "품목 수") int itemCount,
        @Schema(description = "결제 금액") BigDecimal totalAmount,

        @Schema(description = "배송. 아직 없으면 발송 대기 상태로 채워 보낸다")
        ShipmentResponse shipment,

        /**
         * 지금 관리자가 손대야 하는 주문인가.
         *
         * <p>목록에서 "발송 대기 N건"을 세고 줄을 눈에 띄게 하는 데 쓴다. 상태 조합을
         * 화면이 다시 판단하면 백엔드와 갈라진다 — 한 곳에서 정해 보낸다.
         */
        @Schema(description = "발송 대기인가", example = "true") boolean awaitingShipment
) {

    public static AdminOrderSummaryResponse of(Order order, List<OrderItem> items, Shipment shipment) {
        String orderName = buildOrderName(items);
        boolean awaiting = (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.PREPARING)
                && (shipment == null || !shipment.hasTrackingNo());

        return new AdminOrderSummaryResponse(
                order.getId(),
                order.getOrderNo(),
                order.getOrderedAt(),
                order.getStatus(),
                order.getStatus().label(),
                order.getReceiverName(),
                orderName,
                items.size(),
                order.getTotalAmount(),
                shipment == null ? ShipmentResponse.pending() : ShipmentResponse.from(shipment),
                awaiting);
    }

    /** "순금 골드바 3.75g 외 1건". 품목이 없으면(이론상) 빈 문자열이 아니라 안내를 둔다. */
    private static String buildOrderName(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "(품목 없음)";
        }
        String first = items.get(0).getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }
}
