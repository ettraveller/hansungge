package com.hanseongge.domain.cart.controller;

import com.hanseongge.domain.cart.dto.AddCartItemRequest;
import com.hanseongge.domain.cart.dto.CartResponse;
import com.hanseongge.domain.cart.dto.ChangeQuantityRequest;
import com.hanseongge.domain.cart.service.CartService;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 장바구니 API. 전 구간 로그인 필요.
 *
 * <p>비회원 장바구니는 만들지 않는다 — 브라우저에만 있는 장바구니는 기기를 바꾸면
 * 사라지고, 서버에 두면 익명 데이터를 무한정 보관하게 된다.
 *
 * <p>변경 후에는 장바구니 <b>전체</b>를 돌려준다. 한 줄만 돌려주면 화면이 합계·안내
 * 문구를 직접 다시 계산해야 하고, 그러면 서버 계산과 갈라진다 (원칙 P1).
 */
@Tag(name = "장바구니", description = "담기 / 수량변경 / 삭제")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "장바구니 조회",
            description = "현재 시세로 다시 계산해 담을 때 가격과 비교한 결과를 함께 돌려준다. "
                    + "합계에는 지금 주문 가능한 줄만 포함된다.")
    @GetMapping
    public ResponseEntity<CartResponse> view(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.view(principal.id()));
    }

    @Operation(summary = "장바구니 개수", description = "헤더 배지용. 가격 계산 없이 줄 수만 센다.")
    @GetMapping("/count")
    public ResponseEntity<Map<String, Integer>> count(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", cartService.count(principal.id())));
    }

    @Operation(summary = "장바구니 담기",
            description = "금액은 받지 않는다 — 담은 시점 단가는 서버가 계산해 기록한다. "
                    + "시세를 확인할 수 없거나(409) 판매중이 아니거나 재고가 모자라면 담기지 않는다.")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> add(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(cartService.add(
                principal.id(), request.productId(), request.optionId(), request.quantity()));
    }

    @Operation(summary = "수량 변경",
            description = "증감이 아니라 절대 수량이다. 요청이 중복 도착해도 수량이 두 번 늘지 않는다.")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> changeQuantity(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId,
            @Valid @RequestBody ChangeQuantityRequest request) {
        return ResponseEntity.ok(
                cartService.changeQuantity(principal.id(), itemId, request.quantity()));
    }

    @Operation(summary = "항목 삭제")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> remove(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.remove(principal.id(), itemId));
    }

    @Operation(summary = "장바구니 비우기")
    @DeleteMapping
    public ResponseEntity<CartResponse> clear(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.clear(principal.id()));
    }
}
