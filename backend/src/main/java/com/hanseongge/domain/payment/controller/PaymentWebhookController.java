package com.hanseongge.domain.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.domain.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠 웹훅 수신 (2026-09-29).
 *
 * <h2>왜 필요한가</h2>
 * 고객이 결제를 마치고 <b>성공 화면에 돌아오지 못하면</b>(창을 닫았거나, 네트워크가 끊겼거나,
 * 휴대폰 은행 앱에서 돌아오지 못했거나) 승인이 우리 DB 에 남지 않는다.
 * <b>돈은 빠져나갔는데 주문은 미결제</b>인 상태가 된다. 웹훅이 그 구멍을 메운다.
 *
 * <h2>본문을 믿지 않는다</h2>
 * 토스 웹훅에는 서명이 없다 — 이 주소만 알면 누구나 "결제됐다"고 알릴 수 있다. 그래서 본문에서
 * {@code paymentKey} 만 꺼내 쓰고, 서비스가 <b>토스에 직접 되물어</b> 그 응답으로만 판단한다.
 * 본인확인·소셜 로그인에서 쓰는 것과 같은 원칙이다.
 *
 * <h2>항상 200 으로 답한다</h2>
 * 우리 쪽 처리에 실패해도 토스에는 받았다고 답한다. 오류를 돌려주면 토스가 재시도하는데,
 * 실패가 우리 문제(DB 장애 등)라면 재시도도 같은 이유로 실패하면서 알림만 쌓인다.
 * 대신 <b>ERROR 로그를 크게 남겨</b> 사람이 보게 한다.
 */
@Slf4j
@Tag(name = "결제", description = "토스페이먼츠 웹훅")
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @Operation(summary = "토스 웹훅 수신",
            description = "본문의 paymentKey 만 쓰고 실제 상태는 토스에 되물어 확인한다. 항상 200 으로 답한다.")
    @PostMapping("/toss")
    public ResponseEntity<Void> toss(@RequestBody(required = false) JsonNode body) {
        String paymentKey = paymentKeyOf(body);
        if (paymentKey == null) {
            log.warn("[PAY] 웹훅에 paymentKey 가 없습니다. body={}", body);
            return ResponseEntity.ok().build();
        }

        try {
            paymentService.settleFromWebhook(paymentKey);
        } catch (RuntimeException e) {
            log.error("[PAY] ⚠️ 웹훅 처리에 실패했습니다. 수동 확인 필요 paymentKey={}", paymentKey, e);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * {@code paymentKey} 를 꺼낸다.
     *
     * <p>토스는 이벤트 종류에 따라 최상위에 두기도 하고 {@code data} 안에 두기도 한다.
     * 규격이 바뀌어도 조용히 멈추지 않도록 둘 다 본다.
     */
    private static String paymentKeyOf(JsonNode body) {
        if (body == null) {
            return null;
        }
        for (JsonNode node : new JsonNode[] {body, body.get("data")}) {
            if (node == null) {
                continue;
            }
            JsonNode key = node.get("paymentKey");
            if (key != null && !key.isNull() && !key.asText().isBlank()) {
                return key.asText();
            }
        }
        return null;
    }
}
