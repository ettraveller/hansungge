package com.hanseongge.domain.payment.controller;

import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.payment.dto.PaymentConfigResponse;
import com.hanseongge.domain.payment.dto.PaymentConfirmRequest;
import com.hanseongge.domain.payment.service.PaymentService;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 결제 API (토스페이먼츠).
 *
 * <p>{@code /config} 만 로그인 없이 열려 있다 — 결제창 스크립트를 미리 준비하기 위해서이고,
 * 담긴 값(clientKey)은 원래 브라우저에 나가는 공개값이다.
 */
@Tag(name = "결제", description = "토스페이먼츠 결제창 / 승인")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 설정",
            description = "결제창을 띄우는 데 필요한 공개 키. 설정이 없으면 available=false 다.")
    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> config() {
        return ResponseEntity.ok(paymentService.config());
    }

    @Operation(summary = "결제창 진입",
            description = "주문을 '결제 진행 중'으로 옮기고 결제 시도를 기록한다. "
                    + "이 표시가 있어야 결제창까지 갔다 돌아오지 않은 주문을 나중에 가려낼 수 있다.")
    @PostMapping("/{orderNo}/ready")
    public ResponseEntity<OrderResponse> ready(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo) {
        return ResponseEntity.ok(paymentService.ready(principal.id(), orderNo));
    }

    @Operation(summary = "결제 승인",
            description = "서버가 토스에 직접 승인을 요청한다. 금액은 받지 않는다 — "
                    + "DB 의 주문 금액으로 승인하고, 승인 응답 금액과 다시 대조한다.")
    @PostMapping("/confirm")
    public ResponseEntity<OrderResponse> confirm(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(
                paymentService.confirm(principal.id(), request.orderNo(), request.paymentKey()));
    }

    @Operation(summary = "결제 중단 기록",
            description = "고객이 결제창에서 취소했거나 실패했을 때. 주문은 살려 둔다 — "
                    + "다른 수단으로 다시 낼 수 있어야 한다.")
    @PostMapping("/{orderNo}/fail")
    public ResponseEntity<Void> fail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @RequestParam(required = false) String reason) {
        paymentService.markFailed(principal.id(), orderNo, reason);
        return ResponseEntity.noContent().build();
    }
}
