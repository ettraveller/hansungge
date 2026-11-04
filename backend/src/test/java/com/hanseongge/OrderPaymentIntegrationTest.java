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

    // =================================================================
    // 결제수단 — 토스 입점 심사(2026-11-03)가 카드에서 제외한 품목
    //
    // 여기가 깨지면 심사 위반 거래가 PG 로 나간다. 상품을 잘못 등록해서가 아니라
    // <b>규칙 자체가 사라져서</b> 그렇게 되는 경우를 막는 자리다.
    // =================================================================

    @Test
    @DisplayName("순금(24K)은 카드로 주문할 수 없다 — 카드사가 온라인 거래를 제한하는 품목이다")
    void pureGoldCannotBePaidByCard() throws Exception {
        long gold24k = seedProduct("GOLD_24K", "FIXED", "500000");
        onlyInCart(gold24k, 1);

        MvcResult result = createOrderRaw("EASY_PAY", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("무통장입금");
    }

    @Test
    @DisplayName("은도 카드로 주문할 수 없다")
    void silverCannotBePaidByCard() throws Exception {
        long silver = seedProduct("SILVER", "FIXED", "50000");
        onlyInCart(silver, 1);

        assertThat(createOrderRaw("EASY_PAY", null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("18K 라도 시세에 연동되면 카드로 주문할 수 없다 — 고정금액이 아니면 입점 불가다")
    void marketLinkedCannotBePaidByCard() throws Exception {
        long linked = seedProduct("GOLD_18K", "MARKET_LINKED", null);
        onlyInCart(linked, 1);

        assertThat(createOrderRaw("EASY_PAY", null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("카드 가능 상품에 골드바가 한 줄 섞이면 주문 전체가 카드 불가가 된다")
    void oneForbiddenLineBlocksTheWholeOrder() throws Exception {
        long gold24k = seedProduct("GOLD_24K", "FIXED", "500000");
        onlyInCart(optionId, 1);     // 18K 고정가 — 혼자면 카드로 된다
        addToCart(gold24k, 1);

        assertThat(createOrderRaw("EASY_PAY", null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("무통장이면 금종·가격방식과 무관하게 주문된다 — 입금자명과 기한이 함께 남는다")
    void bankTransferAcceptsAnything() throws Exception {
        seedBankAccount();
        long gold24k = seedProduct("GOLD_24K", "FIXED", "500000");
        onlyInCart(gold24k, 2);

        MvcResult result = createOrderRaw("BANK_TRANSFER", "김영희");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode order = read(result);
        assertThat(order.get("paymentMethod").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(order.get("depositorName").asText()).isEqualTo("김영희");
        assertThat(order.get("depositDueAt").asText()).isNotBlank();
        // 결제 전 상태의 문구가 다르다 — 「결제 진행 중」이면 이미 낸 것처럼 읽힌다
        assertThat(order.get("statusLabel").asText()).isEqualTo("입금 대기");
    }

    @Test
    @DisplayName("무통장인데 입금자명이 없으면 주문되지 않는다 — 들어온 돈을 어느 주문에 붙일지 알 수 없다")
    void bankTransferNeedsDepositorName() throws Exception {
        seedBankAccount();
        long gold24k = seedProduct("GOLD_24K", "FIXED", "500000");
        onlyInCart(gold24k, 1);

        assertThat(createOrderRaw("BANK_TRANSFER", "   ").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("입금받을 계좌가 설정되지 않았으면 무통장 주문을 받지 않는다 — 입금할 곳 없는 주문을 만들지 않는다")
    void bankTransferNeedsAccountConfigured() throws Exception {
        jdbc.update("UPDATE system_settings SET value = '' WHERE key = 'BANK_ACCOUNT_NUMBER'");
        long gold24k = seedProduct("GOLD_24K", "FIXED", "500000");
        onlyInCart(gold24k, 1);

        assertThat(createOrderRaw("BANK_TRANSFER", "김영희").getResponse().getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("카드 가능 상품도 무통장으로 받을 수 있다 — 막는 것은 반대 방향뿐이다")
    void cardPayableCanAlsoUseBankTransfer() throws Exception {
        seedBankAccount();
        onlyInCart(optionId, 1);

        MvcResult result = createOrderRaw("BANK_TRANSFER", "홍길동");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(result).get("paymentMethod").asText()).isEqualTo("BANK_TRANSFER");
    }

    @Test
    @DisplayName("장바구니가 알려 준다 — 카드로 결제할 수 있는 장바구니인가")
    void cartTellsWhetherCardIsPossible() throws Exception {
        onlyInCart(optionId, 1);
        assertThat(readCart().get("cardPayable").asBoolean()).isTrue();

        addToCart(seedProduct("GOLD_24K", "FIXED", "500000"), 1);
        assertThat(readCart().get("cardPayable").asBoolean()).isFalse();
    }

    // =================================================================
    // 부가세 (V38) — 상품이 과세인지 면세인지에 따라 주문에 세액이 남는다
    // =================================================================

    @Test
    @DisplayName("면세 상품만 담으면 세액이 0 이고 공급가액 = 상품 합계다")
    void exemptOrderHasNoVat() throws Exception {
        onlyInCart(optionId, 2);     // 픽스처 상품은 면세다

        JsonNode order = read(createOrder());

        assertThat(order.get("vatAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(order.get("supplyAmount").decimalValue())
                .isEqualByComparingTo(order.get("itemsAmount").decimalValue());
    }

    @Test
    @DisplayName("과세 상품의 세액은 수량만큼 곱해진다 — 2개 주문의 세액이 절반이 되지 않는다")
    void taxableOrderMultipliesVatByQuantity() throws Exception {
        // 단가 100,000 짜리 픽스처 상품을 과세로 돌린다
        long productId = jdbc.queryForObject(
                "SELECT product_id FROM product_options WHERE id = ?", Long.class, optionId);
        jdbc.update("UPDATE products SET vat_taxable = TRUE WHERE id = ?", productId);

        onlyInCart(optionId, 2);
        JsonNode order = read(createOrder());

        // 단가 100,000 안에 든 10% 분은 9,090.91. 두 개면 18,181.82 다.
        // 한 개분(9,090.91)만 남으면 수량을 곱하지 않은 것이다.
        assertThat(order.get("itemsAmount").decimalValue()).isEqualByComparingTo("200000");
        assertThat(order.get("vatAmount").decimalValue()).isEqualByComparingTo("18181.82");
        assertThat(order.get("supplyAmount").decimalValue()).isEqualByComparingTo("181818.18");

        // 핵심 — 과세로 돌려도 고객이 내는 돈은 그대로다
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("200000");
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
    @DisplayName("청약철회 제한에 동의하지 않으면 주문이 만들어지지 않는다")
    void withdrawalConsentIsRequired() throws Exception {
        addToCart(optionId, 1);

        MvcResult result = createOrderRaw(false);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(count("orders")).isZero();
        // 재고도 건드리지 않아야 한다 — 만들어지지 않은 주문이다.
        assertThat(stockOf(optionId)).isEqualTo(10);
    }

    @Test
    @DisplayName("동의한 시각을 주문에 남긴다 — 분쟁에서 \"안내했다\" 가 아니라 \"동의했다\" 를 보여야 한다")
    void withdrawalConsentIsRecorded() throws Exception {
        addToCart(optionId, 1);

        createOrder();

        Integer consented = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE withdrawal_consent_at IS NOT NULL", Integer.class);
        assertThat(consented).isEqualTo(1);

        // 어느 내용에 동의했는지까지 남는다 — 환불규정 현재 판의 번호다.
        Long agreedPolicy = jdbc.queryForObject(
                "SELECT refund_policy_id FROM orders LIMIT 1", Long.class);
        assertThat(agreedPolicy).isEqualTo(currentRefundPolicyId());
    }

    @Test
    @DisplayName("읽은 환불규정이 그 사이 개정됐으면 409 — 새 규정으로 다시 동의받는다")
    void staleRefundPolicyIsRejected() throws Exception {
        addToCart(optionId, 1);

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverName", "홍길동",
                                "receiverPhone", "010-1234-5678",
                                "zipcode", "03187",
                                "address1", "서울특별시 종로구 돈화문로 00",
                                "withdrawalConsent", true,
                                // 지금 판보다 하나 전 판 — 개정 직전에 읽은 화면이 보내는 값이다
                                "refundPolicyId", currentRefundPolicyId() - 1))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("REFUND_POLICY_CHANGED");
        assertThat(count("orders")).isZero();
        assertThat(stockOf(optionId)).isEqualTo(10);
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

    /**
     * 결제창에서 「취소」를 누르면 기다리지 않고 바로 풀린다 (2026-10-30).
     *
     * <p>전에는 주문을 살려 뒀다. "다른 카드로 다시 내려 할 때 처음부터 담아야 한다"는
     * 이유였는데 그 전제가 틀렸다 — 장바구니는 결제가 성공해야 비워지므로, 취소한
     * 고객의 장바구니에는 상품이 그대로 있다.
     *
     * <p>대가는 컸다. 재고가 1개인 상품은 누가 결제창까지 갔다 돌아오기만 해도
     * 그동안 다른 사람에게 품절로 보였다.
     */
    @Test
    @DisplayName("결제창에서 취소하면 주문을 접고 재고를 바로 돌려준다 — 장바구니는 그대로다")
    void paymentCancelRestoresStockAtOnce() throws Exception {
        addToCart(optionId, 2);
        String orderNo = read(createOrder()).get("orderNo").asText();
        assertThat(stockOf(optionId)).isEqualTo(8);

        assertThat(mockMvc.perform(post("/api/v1/payments/" + orderNo + "/fail")
                        .param("reason", "USER_CANCEL")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        assertThat(statusOf(orderNo)).isEqualTo("CANCELLED");
        assertThat(stockOf(optionId))
                .as("방치 주문 정리(10분)를 기다리지 않고 바로 돌아와야 한다")
                .isEqualTo(10);

        // 장바구니가 남아 있어야 두 번만 눌러 다시 주문할 수 있다.
        assertThat(count("cart_items")).isEqualTo(1);
    }

    /**
     * 창은 닫았는데 승인이 먼저 들어온 경우.
     *
     * <p>그때 주문을 접으면 <b>받은 돈의 주문을 지우는 것</b>이 된다. 고객은 결제됐는데
     * 주문이 없고, 재고는 돌아가 다른 사람에게 팔린다.
     */
    @Test
    @DisplayName("이미 결제된 주문에는 결제 중단 통지가 와도 접지 않는다")
    void paidOrderSurvivesLateFailNotice() throws Exception {
        addToCart(optionId, 1);
        String orderNo = read(createOrder()).get("orderNo").asText();
        givenTossApproves(orderNo, "100000");
        confirm(orderNo, "pk_late");
        assertThat(statusOf(orderNo)).isEqualTo("PAID");

        assertThat(mockMvc.perform(post("/api/v1/payments/" + orderNo + "/fail")
                        .param("reason", "LATE")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        assertThat(stockOf(optionId))
                .as("결제된 주문의 재고를 돌려주면 한 물건을 두 사람에게 팔게 된다")
                .isEqualTo(9);
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
        return createOrderRaw(true);
    }

    /** 결제수단과 입금자명을 지정해 주문한다. 무통장 경로를 확인할 때 쓴다. */
    private MvcResult createOrderRaw(String paymentMethod, String depositorName) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678",
                "zipcode", "03187",
                "address1", "서울특별시 종로구 돈화문로 00",
                "withdrawalConsent", true,
                "refundPolicyId", currentRefundPolicyId(),
                "paymentMethod", paymentMethod));
        if (depositorName != null) {
            body.put("depositorName", depositorName);
        }
        return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    /** 장바구니를 비우고 주어진 상품 하나만 담는다. */
    private void onlyInCart(long optionId, int quantity) throws Exception {
        jdbc.update("DELETE FROM cart_items");
        addToCart(optionId, quantity);
    }

    /** 금종과 가격방식을 지정해 상품을 세운다 — 결제수단 판정이 이 둘에서 나온다. */
    private long seedProduct(String metalType, String priceType, String unitPrice) {
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO products (category_id, name, metal_type, price_type, fixed_price,
                                      labor_cost, weight_g, status, pricing_model)
                VALUES (?, ?, ?::text, ?, ?::numeric, 0, 3.75, 'ON_SALE', 'SIMPLE')
                """, categoryId, metalType + " " + priceType, metalType, priceType, unitPrice);
        Long productId = jdbc.queryForObject(
                "SELECT id FROM products ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO product_options (product_id, name, stock, sort, active, is_default)
                VALUES (?, '기본', 10, 0, TRUE, TRUE)
                """, productId);
        return jdbc.queryForObject("SELECT id FROM product_options ORDER BY id DESC LIMIT 1", Long.class);
    }

    private JsonNode readCart() throws Exception {
        return read(mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn());
    }

    private void seedBankAccount() {
        jdbc.update("UPDATE system_settings SET value = ? WHERE key = 'BANK_ACCOUNT_BANK'", "국민은행");
        jdbc.update("UPDATE system_settings SET value = ? WHERE key = 'BANK_ACCOUNT_NUMBER'", "123456-78-901234");
        jdbc.update("UPDATE system_settings SET value = ? WHERE key = 'BANK_ACCOUNT_HOLDER'", "주식회사 금한성");
    }

    /** 환불규정 현재 판. 주문 요청은 "이 판에 동의했다" 를 함께 보낸다. */
    private long currentRefundPolicyId() {
        Long id = jdbc.queryForObject("SELECT max(id) FROM refund_policies", Long.class);
        assertThat(id).as("V34 시드가 있어야 한다").isNotNull();
        return id;
    }

    /** 청약철회 제한 동의 여부를 지정해 주문한다. */
    private MvcResult createOrderRaw(boolean withdrawalConsent) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverName", "홍길동",
                                "receiverPhone", "010-1234-5678",
                                "zipcode", "03187",
                                "address1", "서울특별시 종로구 돈화문로 00",
                                "withdrawalConsent", withdrawalConsent,
                                "refundPolicyId", currentRefundPolicyId()))))
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

    /**
     * 시세에 기대지 않도록 고정가 상품으로 세운다 — 주문 금액 단언이 시세 변동에 흔들리면 안 된다.
     *
     * <p><b>18K 고정가</b>로 둔다. 이 파일의 시험은 카드 결제 흐름이고, 카드로 팔 수 있는
     * 상품은 18K·14K 고정가뿐이다 ({@code Product#isCardPayable}). 금종을 비워 두면
     * 무통장 전용으로 떨어져 주문 생성부터 409 가 된다 — 시험이 막히는 것이 아니라
     * 규칙이 제대로 걸리는 것이다.
     */
    private long seedProduct(String unitPrice) {
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO products (category_id, name, metal_type, price_type, fixed_price,
                                      status, pricing_model)
                VALUES (?, '테스트 18K 팔찌', 'GOLD_18K', 'FIXED', ?::numeric, 'ON_SALE', 'SIMPLE')
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
