package com.hanseongge.domain.order.controller;

import com.hanseongge.domain.order.dto.OrderCreateRequest;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.service.OrderService;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
public class OrderController {

    private static final int MAX_SIZE = 50;

    private final OrderService orderService;

    @Operation(summary = "주문서 작성",
            description = "장바구니 전체를 주문으로 만든다. 금액과 상품은 받지 않는다 — "
                    + "서버가 장바구니를 읽고 그 시점 시세로 다시 계산한다.")
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.ok(orderService.createFromCart(principal.id(), request));
    }

    @Operation(summary = "내 주문 목록")
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int capped = Math.min(Math.max(size, 1), MAX_SIZE);
        return ResponseEntity.ok(
                orderService.listMine(principal.id(), PageRequest.of(Math.max(page, 0), capped)));
    }

    @Operation(summary = "주문 상세", description = "남의 주문번호는 404 다.")
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.getMine(principal.id(), orderNo));
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
