package com.hanseongge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.payment.service.PaymentApprovalException;
import com.hanseongge.domain.order.service.StaleOrderSweeper;
import com.hanseongge.domain.payment.service.PaymentService;
import com.hanseongge.domain.payment.service.TossPaymentClient;
import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.SignUpFlow;
import com.hanseongge.support.StubIdentityConfig;
import com.hanseongge.support.StubIdentityVerificationProvider;
import com.hanseongge.support.StubMailConfig;
import com.hanseongge.support.StubOAuthConfig;
import com.hanseongge.support.StubOAuthProvider;
import com.hanseongge.support.StubSmsConfig;
import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 주문(STEP 13)·결제(STEP 14) — 돈이 걸린 경로.
 *
 * <p>여기서 지키려는 것은 하나다: <b>화면이 금액을 정하지 못하게 한다.</b>
 * 주문 금액은 서버가 장바구니를 다시 읽어 계산하고, 승인은 서버가 PG 에 직접 물어
 * 그 응답 금액을 다시 대조한다. 두 곳 중 하나만 있으면, 그 한 곳이 틀렸을 때 막을 것이 없다.
 *
 * <p>PG 는 목으로 세운다. 실제 토스를 부르면 테스트가 네트워크와 토스의 상태에 묶인다 —
 * 우리가 확인하려는 것은 "토스가 잘 도는가"가 아니라 "토스가 무엇을 답하든 우리가 옳게
 * 행동하는가"다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubOAuthConfig.class, StubIdentityConfig.class, StubSmsConfig.class, StubMailConfig.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "app.oauth.redirect-base-url=http://localhost:3000/auth/callback"
})
class OrderPaymentIntegrationTest extends EmbeddedPostgresSupport {

    private static final UserTestFixtures.Person PERSON =
            UserTestFixtures.personWith("CI-ORDER", "홍길동", "01012345678");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubOAuthProvider google;
    @Autowired StubIdentityVerificationProvider identityProvider;
    @Autowired StaleOrderSweeper sweeper;

    /** 토스는 부르지 않는다. 승인 응답만 우리가 정한다. */
    @MockitoBean TossPaymentClient toss;
    @Autowired PaymentService paymentService;

    private SignUpFlow flow;
    private String accessToken;
    private long optionId;

    @BeforeEach
    void setUp() throws Exception {
        google.reset();
        identityProvider.reset();
        UserTestFixtures.deleteAll(jdbc);
        clearOrders();
        flow = new SignUpFlow(mockMvc, objectMapper, google, identityProvider);

        when(toss.isConfigured()).thenReturn(true);
        when(toss.clientKey()).thenReturn("test_gck_x");
        when(toss.isTestMode()).thenReturn(true);

        accessToken = flow.phone(PERSON, SignUpFlow.PASSWORD);
        optionId = seedProduct("100000");
    }

    // =================================================================
    // 주문
    // =================================================================

    @Test
    @DisplayName("주문 금액은 화면이 아니라 서버가 정한다 — 요청에 금액 자리가 없다")
    void orderAmountComesFromServer() throws Exception {
        addToCart(optionId, 2);

        JsonNode order = read(createOrder());

        // 단가 100,000 × 2 = 200,000. 화면은 아무 금액도 보내지 않았다.
        assertThat(order.get("itemsAmount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(order.get("status").asText()).isEqualTo("ORDER_CREATED");
        assertThat(order.get("orderNo").asText()).startsWith("HS");
    }

    @Test
    @DisplayName("주문서를 만들면 재고가 준다 — 장바구니는 아직 그대로다")
    void orderTakesStockButKeepsCart() throws Exception {
        addToCart(optionId, 3);

        createOrder();

        assertThat(stockOf(optionId)).isEqualTo(7);          // 10 - 3
        // 아직 결제 전이다. 여기서 비우면 결제창에서 그만둔 고객이 돌아왔을 때
        // 빈 장바구니를 보고, 무엇을 담았는지 다시 찾아야 한다.
        assertThat(count("cart_items")).isEqualTo(1);
    }

    @Test
    @DisplayName("결제가 승인되면 그때 장바구니가 빈다")
    void cartClearsAfterPayment() throws Exception {
        addToCart(optionId, 3);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "300000");

        confirm(orderNo, "pay-cart-clear");

        assertThat(count("cart_items")).isZero();
    }

    @Test
    @DisplayName("결제하지 않고 주문을 취소하면 장바구니는 그대로 남는다")
    void cancelKeepsCart() throws Exception {
        addToCart(optionId, 3);
        String orderNo = read(createOrder()).get("orderNo").asText();

        mockMvc.perform(post("/api/v1/orders/" + orderNo + "/cancel")
                .header("Authorization", "Bearer " + accessToken)).andReturn();

        // 다시 주문할 수 있어야 한다. 담은 것이 사라지면 처음부터 고르게 된다.
        assertThat(count("cart_items")).isEqualTo(1);
    }

    @Test
    @DisplayName("재고보다 많이 주문하면 아무것도 남지 않는다 — 주문도 재고 차감도")
    void overOrderLeavesNothing() throws Exception {
        addToCart(optionId, 3);
        jdbc.update("UPDATE product_options SET stock = 1 WHERE id = ?", optionId);

        assertThat(createOrderRaw().getResponse().getStatus()).isEqualTo(409);
        assertThat(count("orders")).isZero();
        assertThat(stockOf(optionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 장바구니로는 주문할 수 없다")
    void emptyCartCannotOrder() throws Exception {
        MvcResult result = createOrderRaw();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(result).get("code").asText()).isEqualTo("ORDER_NOTHING_TO_ORDER");
    }

    @Test
    @DisplayName("남의 주문번호는 404 다 — 403 이면 그 번호의 주문이 있다는 것이 샌다")
    void othersOrderIsNotFound() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();

        String otherToken = flow.phone(
                UserTestFixtures.personWith("CI-OTHER", "김철수", "01099998888"), SignUpFlow.PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/v1/orders/" + orderNo)
                        .header("Authorization", "Bearer " + otherToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("결제 전 주문을 취소하면 재고가 돌아온다")
    void cancelRestoresStock() throws Exception {
        addToCart(optionId, 4);
        String orderNo = read(createOrder()).get("orderNo").asText();
        assertThat(stockOf(optionId)).isEqualTo(6);

        mockMvc.perform(post("/api/v1/orders/" + orderNo + "/cancel")
                .header("Authorization", "Bearer " + accessToken)).andReturn();

        assertThat(stockOf(optionId)).isEqualTo(10);
        assertThat(statusOf(orderNo)).isEqualTo("CANCELLED");
    }

    // =================================================================
    // 결제
    // =================================================================

    @Test
    @DisplayName("승인은 브라우저가 보낸 금액이 아니라 주문 금액으로 요청한다")
    void confirmUsesOrderAmountNotBrowserAmount() throws Exception {
        addToCart(optionId, 2);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "200000");

        confirm(orderNo, "pk_1");

        // 200,000 으로 물었는지. 요청 DTO 에 금액 자리가 없으므로 다른 값이 갈 길이 없다.
        verify(toss).confirm(eq("pk_1"), eq(orderNo), eq(new BigDecimal("200000.00")));
        assertThat(statusOf(orderNo)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("승인 금액이 주문 금액과 다르면 결제 완료로 넘기지 않는다")
    void mismatchedApprovalIsRejected() throws Exception {
        addToCart(optionId, 2);
        String orderNo = read(createOrder()).get("orderNo").asText();
        // PG 가 1,000원만 승인했다고 답한다 (금액 조작·PG 오류 어느 쪽이든)
        givenTossApproves(orderNo, "1000");

        MvcResult result = confirmRaw(orderNo, "pk_2");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(result).get("code").asText()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        assertThat(statusOf(orderNo)).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    @DisplayName("같은 승인이 두 번 와도 한 번만 처리한다 — PG 재시도는 정상이다")
    void duplicateApprovalIsIdempotent() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "100000");

        confirm(orderNo, "pk_3");
        MvcResult again = confirm(orderNo, "pk_3");

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        // 두 번째는 토스에 다시 묻지 않는다 — 물으면 이중 승인이 날 수 있다
        verify(toss, times(1)).confirm(anyString(), anyString(), any());
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 가 거절하면 주문은 결제 대기로 남는다 — 다른 수단으로 다시 낼 수 있어야 한다")
    void rejectedApprovalKeepsOrder() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();
        when(toss.confirm(anyString(), anyString(), any()))
                .thenThrow(new PaymentApprovalException("거절", new RuntimeException()));

        MvcResult result = confirmRaw(orderNo, "pk_4");

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(read(result).get("code").asText()).isEqualTo("PAYMENT_APPROVAL_FAILED");
        assertThat(statusOf(orderNo)).isEqualTo("PAYMENT_PENDING");
        // 재고는 여전히 이 주문이 들고 있다. 취소해야 돌아간다
        assertThat(stockOf(optionId)).isEqualTo(9);
    }

    @Test
    @DisplayName("이미 결제된 주문에는 결제창을 다시 띄우지 않는다")
    void paidOrderCannotBePaidAgain() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "100000");
        confirm(orderNo, "pk_5");

        MvcResult result = mockMvc.perform(post("/api/v1/payments/" + orderNo + "/ready")
                .header("Authorization", "Bearer " + accessToken)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("결제 설정은 로그인 없이도 볼 수 있다 — 담긴 것은 공개 키뿐이다")
    void paymentConfigIsPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/payments/config")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("clientKey");
        assertThat(body).doesNotContain("secret");
    }

    // =================================================================
    // 방치 주문 정리 (2026-09-29)
    // =================================================================

    @Test
    @DisplayName("결제 없이 방치된 주문을 접고 재고를 돌려준다")
    void staleOrderIsSweptAndStockReturned() throws Exception {
        addToCart(optionId, 3);
        String orderNo = read(createOrder()).get("orderNo").asText();
        assertThat(stockOf(optionId)).isEqualTo(7);

        agedBy(orderNo, 31);
        sweeper.sweep();

        assertThat(statusOf(orderNo)).isEqualTo("CANCELLED");
        assertThat(stockOf(optionId)).isEqualTo(10);
    }

    @Test
    @DisplayName("아직 시간이 지나지 않은 주문은 건드리지 않는다")
    void freshOrderSurvivesSweep() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();

        agedBy(orderNo, 5);
        sweeper.sweep();

        assertThat(statusOf(orderNo)).isEqualTo("ORDER_CREATED");
        assertThat(stockOf(optionId)).isEqualTo(9);
    }

    @Test
    @DisplayName("결제가 끝난 주문은 오래되어도 접지 않는다 — 이미 받은 돈이다")
    void paidOrderIsNeverSwept() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "100000");
        confirm(orderNo, "pk_sweep");

        agedBy(orderNo, 120);
        sweeper.sweep();

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        assertThat(stockOf(optionId)).isEqualTo(9);
    }

    // =================================================================
    // 웹훅 (2026-09-29)
    // =================================================================

    @Test
    @DisplayName("성공 화면에 돌아오지 못해도 웹훅이 결제를 마무리한다")
    void webhookSettlesWhenCustomerNeverReturns() throws Exception {
        addToCart(optionId, 2);
        String orderNo = read(createOrder()).get("orderNo").asText();
        // 결제창까지는 갔고, 고객은 돌아오지 않았다 (confirm 을 부르지 않는다)
        mockMvc.perform(post("/api/v1/payments/" + orderNo + "/ready")
                .header("Authorization", "Bearer " + accessToken)).andReturn();

        when(toss.lookup("pk_web")).thenReturn(doneOf(orderNo, "200000"));
        webhook("pk_web");

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("웹훅 본문을 믿지 않는다 — paymentKey 로 토스에 되물은 값만 쓴다")
    void webhookTrustsOnlyTossLookup() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();

        // 거짓 알림: 본문에는 결제됐다고 적혀 있지만, 토스는 아직 대기 중이라고 답한다
        when(toss.lookup("pk_fake")).thenReturn(objectMapper.createObjectNode()
                .put("orderId", orderNo)
                .put("status", "WAITING_FOR_DEPOSIT")
                .put("totalAmount", new BigDecimal("100000")));

        webhook("pk_fake");

        assertThat(statusOf(orderNo)).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("웹훅 금액이 주문 금액과 달라도 결제 완료로 넘기지 않는다")
    void webhookAmountMismatchIsRejected() throws Exception {
        addToCart(optionId, 2);
        String orderNo = read(createOrder()).get("orderNo").asText();

        when(toss.lookup("pk_bad")).thenReturn(doneOf(orderNo, "1000"));
        webhook("pk_bad");

        assertThat(statusOf(orderNo)).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("화면이 먼저 처리했으면 웹훅은 토스에 묻지도 않는다")
    void webhookSkipsWhatScreenAlreadyDid() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "100000");
        confirm(orderNo, "pk_dup");

        webhook("pk_dup");

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        verify(toss, never()).lookup("pk_dup");
    }

    @Test
    @DisplayName("웹훅은 우리 쪽이 실패해도 200 으로 답한다 — 오류를 주면 토스가 계속 재시도한다")
    void webhookAlwaysAnswersOk() throws Exception {
        when(toss.lookup(anyString()))
                .thenThrow(new PaymentApprovalException("조회 실패", new RuntimeException()));

        assertThat(webhook("pk_boom").getResponse().getStatus()).isEqualTo(200);
    }

    // =================================================================
    // 도우미
    // =================================================================

    /** 주문한 지 {@code minutes} 분 지난 것으로 만든다. 시계를 돌리지 않고 데이터를 옮긴다. */
    private void agedBy(String orderNo, int minutes) {
        jdbc.update("UPDATE orders SET ordered_at = now() - (? * INTERVAL '1 minute') WHERE order_no = ?",
                minutes, orderNo);
    }

    private MvcResult webhook(String paymentKey) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/webhook/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentKey", paymentKey))))
                .andReturn();
    }

    /** 토스가 "승인 완료"로 답하는 조회 결과. */
    private JsonNode doneOf(String orderNo, String amount) {
        return objectMapper.createObjectNode()
                .put("orderId", orderNo)
                .put("status", "DONE")
                .put("totalAmount", new BigDecimal(amount))
                .put("method", "카드")
                .put("approvedAt", "2026-09-29T15:00:00+09:00");
    }

    private void givenTossApproves(String orderNo, String amount) {
        when(toss.confirm(anyString(), eq(orderNo), any())).thenReturn(approvalOf(orderNo, amount));
    }

    private JsonNode approvalOf(String orderNo, String amount) {
        return objectMapper.createObjectNode()
                .put("orderId", orderNo)
                .put("totalAmount", new BigDecimal(amount))
                .put("method", "카드")
                .put("approvedAt", "2026-09-28T15:00:00+09:00");
    }

    private void addToCart(long optionId, int quantity) throws Exception {
        long productId = jdbc.queryForObject(
                "SELECT product_id FROM product_options WHERE id = ?", Long.class, optionId);
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", productId, "optionId", optionId, "quantity", quantity))))
                .andReturn();
    }

    private MvcResult createOrder() throws Exception {
        MvcResult result = createOrderRaw();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result;
    }

    private MvcResult createOrderRaw() throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverName", "홍길동",
                                "receiverPhone", "010-1234-5678",
                                "zipcode", "03187",
                                "address1", "서울특별시 종로구 돈화문로 00"))))
                .andReturn();
    }

    private MvcResult confirm(String orderNo, String paymentKey) throws Exception {
        MvcResult result = confirmRaw(orderNo, paymentKey);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result;
    }

    private MvcResult confirmRaw(String orderNo, String paymentKey) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + orderNo + "/ready")
                .header("Authorization", "Bearer " + accessToken)).andReturn();
        return mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderNo", orderNo, "paymentKey", paymentKey))))
                .andReturn();
    }

    /** 시세에 기대지 않도록 고정가 상품으로 세운다 — 주문 금액 단언이 시세 변동에 흔들리면 안 된다. */
    private long seedProduct(String unitPrice) {
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO products (category_id, name, price_type, fixed_price, status, pricing_model)
                VALUES (?, '테스트 골드바', 'FIXED', ?::numeric, 'ON_SALE', 'SIMPLE')
                """, categoryId, unitPrice);
        Long productId = jdbc.queryForObject(
                "SELECT id FROM products ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO product_options (product_id, name, stock, sort, active, is_default)
                VALUES (?, '기본', 10, 0, TRUE, TRUE)
                """, productId);
        return jdbc.queryForObject("SELECT id FROM product_options ORDER BY id DESC LIMIT 1", Long.class);
    }

    private void clearOrders() {
        jdbc.update("DELETE FROM payment_transactions");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM order_item_price_components");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM carts");
    }

    private int stockOf(long optionId) {
        return jdbc.queryForObject("SELECT stock FROM product_options WHERE id = ?", Integer.class, optionId);
    }

    private String statusOf(String orderNo) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE order_no = ?", String.class, orderNo);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
