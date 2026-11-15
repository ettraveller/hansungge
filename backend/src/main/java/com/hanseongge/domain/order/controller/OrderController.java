package com.hanseongge.domain.order.controller;

import com.hanseongge.domain.cart.dto.CartResponse;
import com.hanseongge.domain.cart.service.CartService;
import com.hanseongge.domain.order.dto.OrderCreateRequest;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.dto.OrderStatusGroup;
import com.hanseongge.domain.order.service.OrderService;
import com.hanseongge.domain.shipment.dto.ShipmentResponse;
import com.hanseongge.domain.shipment.service.ShipmentService;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 주문 API. 전 구간 로그인 필요.
 *
 * <p>주문번호로만 조회한다. 내부 id 를 주소에 노출하면 숫자를 하나씩 바꿔 가며
 * 남의 주문을 찔러 볼 수 있다 — 소유자 검사를 하더라도 "몇 번까지 주문이 있는지"는 샌다.
 */
@Tag(name = "주문", description = "주문서 작성 / 조회 / 취소")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated // 쿼리 파라미터의 @Min·@Max 가 동작하려면 클래스에 걸어야 한다
public class OrderController {

    private static final int MAX_SIZE = 50;

    private final OrderService orderService;
    private final ShipmentService shipmentService;
    private final CartService cartService;

    @Operation(summary = "주문서 작성",
            description = "장바구니 전체를 주문으로 만든다. directItem 을 보내면 장바구니를 "
                    + "읽지 않고 그 한 품목만 주문한다(바로 구매). 금액은 받지 않는다 — "
                    + "서버가 그 시점 시세로 다시 계산한다.")
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.ok(orderService.createFromCart(principal.id(), request));
    }

    @Operation(summary = "바로 구매 미리보기",
            description = "장바구니를 거치지 않는 한 품목짜리 주문서 화면용. 모양은 장바구니 "
                    + "조회와 같다 — 주문서가 두 흐름을 한 코드로 그린다.")
    @GetMapping("/direct-preview")
    public ResponseEntity<CartResponse> directPreview(
            @RequestParam Long productId,
            @RequestParam(required = false) Long optionId,
            @RequestParam @Min(1) @Max(99) int quantity) {
        return ResponseEntity.ok(cartService.directPreview(productId, optionId, quantity));
    }

    @Operation(summary = "내 주문 목록",
            description = "상태 묶음(group)과 기간(from·to)으로 거를 수 있다. 전부 선택이며, "
                    + "없으면 전체를 최신순으로 준다. 기간은 [from, to) 로 해석한다.")
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) OrderStatusGroup group,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int capped = Math.min(Math.max(size, 1), MAX_SIZE);
        return ResponseEntity.ok(orderService.listMine(principal.id(), group, from, to,
                PageRequest.of(Math.max(page, 0), capped)));
    }

    @Operation(summary = "주문 상세", description = "남의 주문번호는 404 다.")
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.getMine(principal.id(), orderNo));
    }

    @Operation(summary = "내 주문의 배송 정보",
            description = "송장번호와 택배사 조회 페이지 주소를 준다. 발송 전이면 상태만 "
                    + "「발송 대기」로 돌려준다 — 배송 행이 아직 없는 것과 구분하지 않는다. "
                    + "남의 주문번호는 404 다.")
    @GetMapping("/{orderNo}/shipment")
    public ResponseEntity<ShipmentResponse> shipment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo) {
        return ResponseEntity.ok(shipmentService.mine(principal.id(), orderNo));
    }

    @Operation(summary = "주문 취소",
            description = "결제 전에만 된다. 가져갔던 재고를 돌려준다. "
                    + "결제 후 취소·환불은 고객센터가 처리한다.")
    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.cancel(principal.id(), orderNo));
    }
}
