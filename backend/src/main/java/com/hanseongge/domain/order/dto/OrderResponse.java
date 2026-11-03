package com.hanseongge.domain.order.dto;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.entity.OrderStatus;
import com.hanseongge.domain.order.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 주문 상세·목록 공용 응답.
 *
 * <p>금액은 <b>주문 시점 스냅샷</b>이다. 장바구니와 달리 다시 계산하지 않는다 —
 * 이미 낸 돈이 조회할 때마다 달라지면 안 된다.
 */
@Schema(name = "OrderResponse", description = "주문")
public record OrderResponse(

        @Schema(description = "주문번호", example = "HS20260928-000123") String orderNo,
        @Schema(description = "상태 코드", example = "PAID") OrderStatus status,
        @Schema(description = "상태 문구", example = "결제 완료") String statusLabel,

        @Schema(description = "상품 합계") BigDecimal itemsAmount,
        @Schema(description = "배송비") BigDecimal shippingFee,
        @Schema(description = "할인") BigDecimal discountAmount,
        @Schema(description = "결제(예정) 금액") BigDecimal totalAmount,

        @Schema(description = "주문 대표 이름", example = "순금 골드바 3.75g 외 2건") String orderName,
        @Schema(description = "주문 항목") List<Item> items,

        @Schema(description = "받는 사람") String receiverName,
        @Schema(description = "연락처") String receiverPhone,
        @Schema(description = "우편번호") String zipcode,
        @Schema(description = "기본 주소") String address1,
        @Schema(description = "상세 주소") String address2,
        @Schema(description = "요청사항") String orderMemo,

        @Schema(description = "주문 일시") OffsetDateTime orderedAt,
        @Schema(description = "지금 결제할 수 있는가") boolean payable,
        @Schema(description = "지금 취소할 수 있는가") boolean cancellable,

        @Schema(description = "EASY_PAY(카드·간편결제) / BANK_TRANSFER(무통장입금)")
        PaymentMethod paymentMethod,
        @Schema(description = "입금자명. 무통장입금 주문에만 있다") String depositorName,
        @Schema(description = "입금 안내 기한. 지나도 자동취소하지 않는다") OffsetDateTime depositDueAt
) {

    @Schema(name = "OrderItemResponse", description = "주문 항목 (주문 시점 스냅샷)")
    public record Item(
            Long productId,
            @Schema(description = "주문한 규격. 같은 규격으로 다시 담을 때 쓴다. 옵션 없는 상품이면 null")
            Long optionId,
            String productName,
            String optionName,
            MetalType metalType,
            BigDecimal weightG,
            @Schema(description = "적용 시세(원/g). 시세 미연동이면 null") BigDecimal marketPrice,
            BigDecimal finalUnitPrice,
            int quantity,
            BigDecimal finalAmount,
            @Schema(description = "대표이미지 키. 스냅샷이 아니라 지금 상품의 사진이다. "
                    + "상품이 지워졌거나 사진이 없으면 null")
            String thumbnailKey
    ) {
        static Item of(OrderItem item, Map<Long, String> thumbnails) {
            return new Item(item.getProductId(), item.getOptionId(),
                    item.getProductName(), item.getOptionName(),
                    item.getMetalType(), item.getWeightG(), item.getMarketPrice(),
                    item.getFinalUnitPrice(), item.getQuantity(), item.getFinalAmount(),
                    thumbnails.get(item.getProductId()));
        }
    }

    /**
     * 사진 없이. 결제 쪽처럼 화면에 그림을 쓰지 않는 자리가 쓴다.
     *
     * <p>사진을 채우려면 상품을 한 번 더 읽어야 하는데, 결제 승인 응답에는 쓸 데가 없다.
     */
    public static OrderResponse of(Order order, List<OrderItem> items) {
        return of(order, items, Map.of());
    }

    /**
     * 사진까지.
     *
     * <p>{@code thumbnails} 는 상품 id → 대표이미지 키다. <b>스냅샷이 아니다</b> — 금액·상품명과
     * 달리 사진은 지금 상품의 것을 보여 준다. 사진이 주문 당시와 달라졌다고 분쟁이 되지는
     * 않고, 주문 항목에 사진을 복사해 두면 상품 사진을 고칠 때마다 지나간 주문이 옛 사진에
     * 묶인다. 상품이 지워졌으면 비어 있고, 화면은 사진 없는 자리로 그린다.
     */
    public static OrderResponse of(Order order, List<OrderItem> items, Map<Long, String> thumbnails) {
        BigDecimal itemsAmount = items.stream()
                .map(OrderItem::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderResponse(
                order.getOrderNo(),
                order.getStatus(),
                // 무통장은 결제 전 상태의 뜻이 다르다 — 「결제 진행 중」이 아니라 「입금 대기」다
                order.getStatus().label(order.isBankTransfer()),
                itemsAmount,
                order.getShippingFee(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                orderNameOf(items),
                items.stream().map(item -> Item.of(item, thumbnails)).toList(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getZipcode(),
                order.getAddress1(),
                order.getAddress2(),
                order.getOrderMemo(),
                order.getOrderedAt(),
                order.getStatus().isPayable(),
                order.getStatus().isCancellableByCustomer(),
                order.getPaymentMethod(),
                order.getDepositorName(),
                order.getDepositDueAt());
    }

    /**
     * 결제창·목록에 쓰는 한 줄 이름 — "첫 상품 외 N건".
     *
     * <p>PG 결제창에도 이 문자열이 그대로 올라간다. 상품명을 전부 이어 붙이면 길이 제한에
     * 걸리고, 고객의 카드 명세서에도 잘린 채로 남는다.
     */
    private static String orderNameOf(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "주문";
        }
        String first = items.get(0).getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }
}
