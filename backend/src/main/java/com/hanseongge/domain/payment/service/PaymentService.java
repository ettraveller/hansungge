package com.hanseongge.domain.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.service.OrderService;
import com.hanseongge.domain.payment.dto.PaymentConfigResponse;
import com.hanseongge.domain.payment.entity.Payment;
import com.hanseongge.domain.payment.entity.PaymentTransaction;
import com.hanseongge.domain.payment.repository.PaymentRepository;
import com.hanseongge.domain.payment.repository.PaymentTransactionRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 결제 (STEP 14) — 토스페이먼츠.
 *
 * <h2>승인 흐름</h2>
 * <pre>
 *   1. 화면이 /payments/ready 로 결제 정보를 받는다 (clientKey, 주문번호, 금액)
 *   2. 브라우저가 토스 결제창을 띄운다
 *   3. 토스가 successUrl 로 paymentKey·orderId·amount 를 붙여 돌려보낸다
 *   4. 화면이 /payments/confirm 을 부르고, **서버가** 토스에 승인을 요청한다
 * </pre>
 *
 * <h2>브라우저가 보낸 금액을 쓰지 않는다</h2>
 * 4번에서 토스에 보내는 금액은 <b>우리 DB 의 주문 금액</b>이다. 쿼리로 넘어온 금액을 그대로
 * 보내면, 주소창에서 {@code amount} 를 1000 으로 고친 요청이 그대로 승인된다.
 * 승인 응답의 금액도 주문 금액과 다시 대조한다 — 확인을 한 곳에만 두면 그 한 곳이 틀렸을 때
 * 막을 것이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TossPaymentClient tossClient;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /** 화면이 결제창을 띄우기 위해 필요한 것. clientKey 는 공개해도 되는 값이다. */
    @Transactional(readOnly = true)
    public PaymentConfigResponse config() {
        return new PaymentConfigResponse(
                tossClient.isConfigured(), tossClient.clientKey(), tossClient.isTestMode());
    }

    /**
     * 결제창을 띄우기 직전. 주문을 "결제 진행 중"으로 옮기고 결제 시도 행을 만든다.
     *
     * <p>여기서 상태를 옮겨 두면, 나중에 <b>결제창까지 갔다가 돌아오지 않은 주문</b>을
     * 가려낼 수 있다. 그 주문들은 사람이 PG 관리자 화면과 대조해야 하는데, 표시가 없으면
     * 무엇을 대조해야 하는지조차 알 수 없다.
     */
    @Transactional
    public OrderResponse ready(Long userId, String orderNo) {
        if (!tossClient.isConfigured()) {
            throw new BusinessException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        Order order = orderService.requireOrder(userId, orderNo);
        order.markPaymentPending();

        paymentRepository.save(Payment.ready(
                order.getId(), TossPaymentClient.PROVIDER, order.getTotalAmount()));

        List<OrderItem> items = orderService.itemsOf(order.getId());
        log.info("[PAY] 결제창 진입 orderNo={} amount={}", orderNo, order.getTotalAmount());
        return OrderResponse.of(order, items);
    }

    /**
     * 승인. 토스에 실제로 물어보고, 금액이 맞을 때만 주문을 결제 완료로 옮긴다.
     *
     * @param paymentKey 토스가 성공 주소에 붙여 보낸 거래 키
     */
    @Transactional
    public OrderResponse confirm(Long userId, String orderNo, String paymentKey) {
        if (!tossClient.isConfigured()) {
            throw new BusinessException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        Order order = orderService.requireOrder(userId, orderNo);
        List<OrderItem> items = orderService.itemsOf(order.getId());

        // 같은 승인이 두 번 도착하는 것은 PG 에서 정상이다(재시도·새로고침).
        // 이미 처리했으면 오류가 아니라 기존 결과를 돌려준다.
        String idempotencyKey = TossPaymentClient.PROVIDER + ":APPROVE:" + paymentKey;
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("[PAY] 이미 처리한 승인입니다. orderNo={}", orderNo);
            return OrderResponse.of(order, items);
        }

        Payment payment = latestReady(order)
                .orElseGet(() -> paymentRepository.save(Payment.ready(
                        order.getId(), TossPaymentClient.PROVIDER, order.getTotalAmount())));

        JsonNode response;
        try {
            // ⚠️ 브라우저가 보낸 금액이 아니라 **주문 금액**을 보낸다.
            response = tossClient.confirm(paymentKey, orderNo, order.getTotalAmount());
        } catch (PaymentApprovalException e) {
            payment.fail();
            record(payment, "APPROVE", requestOf(paymentKey, orderNo, order.getTotalAmount()),
                    null, false, null);
            throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
        }

        BigDecimal approved = approvedAmountOf(response);
        // 승인은 됐는데 금액이 다르다 — 여기서 멈추면 고객 돈이 묶인다. 사람이 즉시 봐야 한다.
        if (approved == null || approved.compareTo(order.getTotalAmount()) != 0) {
            log.error("[PAY] ⚠️ 승인 금액 불일치. 수동 취소 필요 orderNo={} 주문={} 승인={} paymentKey={}",
                    orderNo, order.getTotalAmount(), approved, paymentKey);
            payment.fail();
            record(payment, "APPROVE", requestOf(paymentKey, orderNo, order.getTotalAmount()),
                    response, false, idempotencyKey);
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        payment.approve(paymentKey, textOf(response, "method"), approved, approvedAtOf(response));
        order.markPaid(approved);
        record(payment, "APPROVE", requestOf(paymentKey, orderNo, order.getTotalAmount()),
                response, true, idempotencyKey);

        log.info("[PAY] 승인 완료 orderNo={} amount={} method={}", orderNo, approved, payment.getMethod());
        return OrderResponse.of(order, items);
    }

    /**
     * 결제창에서 고객이 취소하거나 실패했을 때. 주문은 살려 둔다.
     *
     * <p>주문까지 취소하면 고객이 다른 카드로 다시 내려 할 때 처음부터 담아야 한다.
     * 재고도 계속 들고 있게 되므로, 주문을 접는 것은 고객이 직접 취소할 때만이다.
     */
    @Transactional
    public void markFailed(Long userId, String orderNo, String reason) {
        Order order = orderService.requireOrder(userId, orderNo);
        latestReady(order).ifPresent(payment -> {
            payment.fail();
            record(payment, "APPROVE", Map.of("reason", reason == null ? "" : reason),
                    null, false, null);
        });
        log.info("[PAY] 결제 중단 orderNo={} reason={}", orderNo, reason);
    }

    // =================================================================
    // 내부
    // =================================================================

    private java.util.Optional<Payment> latestReady(Order order) {
        return paymentRepository.findByOrderIdOrderByIdDesc(order.getId()).stream()
                .filter(payment -> !payment.isApproved())
                .findFirst();
    }

    private Map<String, Object> requestOf(String paymentKey, String orderNo, BigDecimal amount) {
        return Map.of("paymentKey", paymentKey, "orderId", orderNo, "amount", amount);
    }

    /**
     * 요청·응답 원문을 남긴다.
     *
     * <p>기록에 실패해도 결제 자체를 되돌리지 않는다 — 승인은 이미 일어난 일이고,
     * 기록이 없다고 고객의 결제를 무를 수는 없다. 대신 로그로 크게 남긴다.
     */
    private void record(Payment payment, String txType, Object request, JsonNode response,
                        boolean success, String idempotencyKey) {
        try {
            transactionRepository.save(PaymentTransaction.of(
                    payment.getId(), txType,
                    objectMapper.writeValueAsString(request),
                    response == null ? null : objectMapper.writeValueAsString(masked(response)),
                    success, idempotencyKey));
        } catch (Exception e) {
            log.error("[PAY] ⚠️ 거래 기록을 남기지 못했습니다. paymentId={} success={}",
                    payment.getId(), success, e);
        }
    }

    /**
     * 카드번호를 가린다.
     *
     * <p>토스는 이미 마스킹해서 주지만, 규격이 바뀌면 그 순간부터 우리 DB 에 카드번호가
     * 쌓인다. 받은 값을 그대로 믿지 않는다.
     */
    private JsonNode masked(JsonNode response) {
        JsonNode card = response.get("card");
        if (card != null && card.has("number")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) card).put("number", "****");
        }
        return response;
    }

    private static BigDecimal approvedAmountOf(JsonNode response) {
        JsonNode node = response == null ? null : response.get("totalAmount");
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }

    private static OffsetDateTime approvedAtOf(JsonNode response) {
        String text = textOf(response, "approvedAt");
        try {
            return text == null ? null : OffsetDateTime.parse(text);
        } catch (RuntimeException e) {
            // 시각을 못 읽었다고 승인을 무를 수는 없다. 지금 시각으로 둔다.
            log.warn("[PAY] approvedAt 을 읽지 못했습니다: {}", text);
            return null;
        }
    }

    private static String textOf(JsonNode response, String field) {
        JsonNode node = response == null ? null : response.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
