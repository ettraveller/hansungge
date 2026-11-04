package com.hanseongge.domain.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 장바구니 전체.
 *
 * <h2>합계는 "지금 주문 가능한 줄"만 더한다</h2>
 * 시세문의·품절·재고부족 줄은 금액이 없거나 주문할 수 없으므로 합계에 넣지 않는다.
 * 넣으면 화면의 합계와 실제 결제 금액이 어긋나고, 사용자는 결제 단계에서야 그 사실을 안다.
 * 대신 {@link #unavailableCount} 로 "빠진 줄이 있다"는 사실을 함께 알린다.
 *
 * <p>이 합계도 <b>결제 금액이 아니다.</b> 주문을 만들 때 서버가 처음부터 다시 계산한다
 * (개발계획 §10.7, 원칙 P1). 여기 값은 화면 표시용이다.
 */
@Schema(name = "CartResponse", description = "장바구니")
public record CartResponse(

        @Schema(description = "담긴 항목") List<CartItemResponse> items,

        @Schema(description = "항목 수(줄 수)", example = "3") int itemCount,
        @Schema(description = "총 수량(수량 합)", example = "5") int totalQuantity,

        @Schema(description = "주문 가능한 줄의 합계(원)", example = "1803900") BigDecimal subtotal,
        @Schema(description = "합계 표시 문구", example = "1,803,900") String subtotalText,

        @Schema(description = "지금 주문할 수 없는 줄 수", example = "1") int unavailableCount,
        @Schema(description = "담을 때보다 가격이 바뀐 줄 수", example = "2") int priceChangedCount,
        @Schema(description = "주문 가능한 줄이 하나라도 있는가", example = "true") boolean orderable,

        /**
         * 이 장바구니를 카드·간편결제로 결제할 수 있는가.
         *
         * <h2>한 줄이라도 안 되면 전체가 안 된다</h2>
         * 순금·순은·골드바·24K·시세 연동 상품은 카드사가 온라인 거래를 제한해 PG 심사를
         * 통과하지 못한다. 섞인 장바구니에서 카드를 허용하려면 주문을 둘로 쪼개야 하고,
         * 그러면 배송비·재고·취소가 전부 두 벌이 된다. 쪼개는 일은 고객이 장바구니에서 한다.
         *
         * <p><b>주문할 수 있는 줄만</b> 본다. 품절된 골드바 한 줄 때문에 나머지가 카드로
         * 결제되지 못하면, 고객은 살 수도 없는 상품에 막혀 있는 셈이 된다.
         */
        @Schema(description = "카드·간편결제 가능. false 면 무통장입금만", example = "true")
        boolean cardPayable,

        /**
         * 배송비(원). 지금 설정으로는 늘 0 이다.
         *
         * <p>화면이 "무료"를 글자로 박지 않도록 값으로 내려보낸다. 박아 두면 배송비를
         * 받기로 한 날 설정만 바꿔서는 화면이 따라오지 않고, <b>장바구니는 무료라는데
         * 주문서에서 돈이 붙는다.</b> 주문서와 같은 계산({@code OrderService#shippingFeeFor})
         * 을 지난 값이다.
         */
        @Schema(description = "배송비(원). 0 이면 무료", example = "0")
        BigDecimal shippingFee,

        /**
         * 결제 예정 금액 = 주문 가능한 줄의 합계 + 배송비.
         *
         * <p>화면이 더하지 않는다 (원칙 P1). 이 값도 결제 금액은 아니다 — 주문을 만들 때
         * 서버가 처음부터 다시 계산한다.
         */
        @Schema(description = "결제 예정 금액(원) = 합계 + 배송비", example = "1803900")
        BigDecimal payableTotal,

        @Schema(description = "화면 상단에 띄울 안내. 없으면 null") String notice
) {

    public static CartResponse of(List<CartItemResponse> items, BigDecimal subtotal,
                                 BigDecimal shippingFee) {
        int unavailable = (int) items.stream().filter(i -> !i.orderable()).count();
        int changed = (int) items.stream().filter(CartItemResponse::priceChanged).count();
        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        boolean orderable = items.size() > unavailable;

        // 주문에 실제로 들어갈 줄만 본다 — 주문 불가 줄은 주문서에 실리지 않는다.
        boolean cardPayable = items.stream()
                .filter(CartItemResponse::orderable)
                .allMatch(CartItemResponse::cardPayable);

        return new CartResponse(
                items,
                items.size(),
                totalQuantity,
                subtotal,
                subtotal == null ? "-" : subtotal.toPlainString(),
                unavailable,
                changed,
                orderable,
                cardPayable,
                shippingFee,
                (subtotal == null ? BigDecimal.ZERO : subtotal).add(shippingFee),
                noticeOf(unavailable, changed));
    }

    public static CartResponse empty() {
        return new CartResponse(List.of(), 0, 0, BigDecimal.ZERO, "0", 0, 0, false, true,
                BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /**
     * 안내 문구.
     *
     * <p>주문을 막는 문제(주문 불가 줄)를 가격 변동보다 먼저 알린다 — 사용자가 결제
     * 버튼을 눌렀다가 막히는 것보다, 누르기 전에 아는 편이 낫다.
     */
    private static String noticeOf(int unavailable, int changed) {
        if (unavailable > 0 && changed > 0) {
            return "주문할 수 없는 상품이 " + unavailable + "건, 가격이 바뀐 상품이 " + changed + "건 있습니다.";
        }
        if (unavailable > 0) {
            return "주문할 수 없는 상품이 " + unavailable + "건 있습니다. 확인 후 삭제해 주세요.";
        }
        if (changed > 0) {
            return "담아두신 뒤 시세가 바뀌어 " + changed + "건의 가격이 변경되었습니다.";
        }
        return null;
    }
}
