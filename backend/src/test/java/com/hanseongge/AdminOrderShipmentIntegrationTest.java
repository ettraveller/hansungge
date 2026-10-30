package com.hanseongge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.support.AdminTestFixtures;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 주문 조회 · 배송 처리.
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>「발송 대기」의 뜻 — 결제완료·준비중이면서 <b>송장번호가 없는</b> 주문</li>
 *   <li>송장을 넣으면 주문과 배송이 <b>함께</b> 배송중이 된다</li>
 *   <li>같은 송장번호를 두 주문에 넣을 수 없다 — 한쪽 고객이 남의 배송을 조회하게 된다</li>
 *   <li>번호를 고쳐도 <b>발송 시각은 처음 값</b>을 지킨다</li>
 *   <li>수령 기록은 되돌아가지 않고, 발송 전에는 남길 수 없다</li>
 *   <li>조회는 OPERATOR, 배송 처리는 MANAGER 이상</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-order-ship-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000"
})
class AdminOrderShipmentIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserJwtProvider userJwtProvider;

    private String managerToken;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        AdminUser manager = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "ordermgr", AdminRole.MANAGER);
        managerToken = login(manager.getLoginId());
        userId = insertUser();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.execute("ALTER TABLE order_items DISABLE TRIGGER trg_order_item_immutable");
        jdbcTemplate.execute(
                "ALTER TABLE order_item_price_components DISABLE TRIGGER trg_oipc_immutable");
        try {
            jdbcTemplate.update("DELETE FROM shipments");
            jdbcTemplate.update("DELETE FROM order_item_price_components");
            jdbcTemplate.update("DELETE FROM order_items");
            jdbcTemplate.update("DELETE FROM orders");
            jdbcTemplate.update("DELETE FROM audit_logs");
            jdbcTemplate.update("DELETE FROM users WHERE id >= 900000");
            jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?",
                    AdminTestFixtures.PREFIX + "%");
        } finally {
            jdbcTemplate.execute(
                    "ALTER TABLE order_item_price_components ENABLE TRIGGER trg_oipc_immutable");
            jdbcTemplate.execute("ALTER TABLE order_items ENABLE TRIGGER trg_order_item_immutable");
        }
    }

    // =================================================================
    // 목록
    // =================================================================

    @Nested
    @DisplayName("주문 목록")
    class Listing {

        @Test
        @DisplayName("발송 대기는 「결제완료·준비중 + 송장번호 없음」이다 — 상태 하나로는 표현되지 않는다")
        void awaitingMeansUnshipped() throws Exception {
            long paid = insertOrder("HS-PAID", "PAID", "김결제");
            long preparing = insertOrder("HS-PREP", "PREPARING", "이준비");
            long shipped = insertOrder("HS-SHIP", "PAID", "박발송");
            long cancelled = insertOrder("HS-CANC", "CANCELLED", "최취소");

            // 하나는 이미 송장을 넣었다 — 상태는 결제완료 그대로지만 발송 대기가 아니다.
            ship(shipped, "6011111111111");

            mockMvc.perform(get("/api/admin/orders/awaiting-count").header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2));

            MvcResult result = mockMvc.perform(get("/api/admin/orders")
                            .param("awaitingShipment", "true")
                            .header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andReturn();

            List<String> nos = orderNosOf(result);
            assertThat(nos).containsExactlyInAnyOrder("HS-PAID", "HS-PREP");
            assertThat(nos).doesNotContain("HS-SHIP", "HS-CANC");
            assertThat(paid).isPositive();
            assertThat(preparing).isPositive();
            assertThat(cancelled).isPositive();
        }

        @Test
        @DisplayName("주문번호와 받는 분 이름을 한 칸으로 찾는다")
        void searchByKeyword() throws Exception {
            insertOrder("HS20261030-000001", "PAID", "김한성");
            insertOrder("HS20261029-000002", "PAID", "이금성");

            assertThat(orderNosOf(search("HS20261030"))).containsExactly("HS20261030-000001");
            assertThat(orderNosOf(search("금성"))).containsExactly("HS20261029-000002");
        }

        @Test
        @DisplayName("조건이 없으면 전체를 최신순으로 준다 — 아침에 여는 화면이 비어 있으면 안 된다")
        void noConditionReturnsAll() throws Exception {
            insertOrder("HS-1", "PAID", "가");
            insertOrder("HS-2", "DELIVERED", "나");

            mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("목록에 대표 상품명과 품목 수가 함께 온다")
        void showsOrderName() throws Exception {
            long id = insertOrder("HS-ITEMS", "PAID", "김상품");
            insertItem(id, "순금 골드바 3.75g");
            insertItem(id, "18K 팔찌 12g");

            MvcResult result = search(null);
            var row = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("content").get(0);
            assertThat(row.get("orderName").asText()).isEqualTo("순금 골드바 3.75g 외 1건");
            assertThat(row.get("itemCount").asInt()).isEqualTo(2);
        }
    }

    // =================================================================
    // 발송
    // =================================================================

    @Nested
    @DisplayName("발송 처리")
    class Shipping {

        @Test
        @DisplayName("송장을 넣으면 주문과 배송이 함께 배송중이 된다")
        void shipMovesBoth() throws Exception {
            long id = insertOrder("HS-SHIP1", "PAID", "김발송");

            mockMvc.perform(put("/api/admin/orders/" + id + "/shipment")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("trackingNo", "6012345678901"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.carrier").value("KOREA_POST"))
                    .andExpect(jsonPath("$.carrierName").value("우체국택배"))
                    .andExpect(jsonPath("$.trackingNo").value("6012345678901"));

            assertThat(statusOf(id)).isEqualTo("SHIPPED");
        }

        @Test
        @DisplayName("하이픈·공백이 섞여 들어와도 숫자만 저장한다 — 같은 번호가 두 모양으로 남지 않게")
        void stripsNonDigits() throws Exception {
            long id = insertOrder("HS-SHIP2", "PAID", "김공백");

            mockMvc.perform(put("/api/admin/orders/" + id + "/shipment")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("trackingNo", " 6012-3456-78901 "))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trackingNo").value("6012345678901"));
        }

        @Test
        @DisplayName("자릿수가 다르면 거절한다 — 우체국택배는 13자리")
        void rejectsWrongLength() throws Exception {
            long id = insertOrder("HS-SHIP3", "PAID", "김자리");

            mockMvc.perform(put("/api/admin/orders/" + id + "/shipment")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("trackingNo", "12345"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_TRACKING_NO_INVALID"));

            assertThat(statusOf(id)).isEqualTo("PAID");
        }

        @Test
        @DisplayName("같은 송장번호를 다른 주문에 넣을 수 없다 — 한쪽 고객이 남의 배송을 조회하게 된다")
        void rejectsDuplicateTrackingNo() throws Exception {
            long first = insertOrder("HS-DUP1", "PAID", "가");
            long second = insertOrder("HS-DUP2", "PAID", "나");
            ship(first, "6012345678901");

            mockMvc.perform(put("/api/admin/orders/" + second + "/shipment")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("trackingNo", "6012345678901"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_TRACKING_NO_DUPLICATED"));

            assertThat(statusOf(second)).isEqualTo("PAID");
        }

        @Test
        @DisplayName("번호를 고쳐도 발송 시각은 처음 값을 지킨다 — 고치면서 시각이 바뀌면 언제 보냈는지가 사라진다")
        void keepsShippedAtOnCorrection() throws Exception {
            long id = insertOrder("HS-FIX", "PAID", "김수정");
            ship(id, "6011111111111");
            String firstShippedAt = shippedAtOf(id);

            ship(id, "6022222222222");

            assertThat(trackingNoOf(id)).isEqualTo("6022222222222");
            assertThat(shippedAtOf(id)).isEqualTo(firstShippedAt);
        }

        @Test
        @DisplayName("발송하면 감사로그가 남는다 — 누가 어느 번호로 보냈다고 했는지가 분쟁의 근거다")
        void writesAuditLog() throws Exception {
            long id = insertOrder("HS-AUDIT", "PAID", "김기록");
            ship(id, "6012345678901");

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT * FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1",
                    AuditActions.ORDER_SHIP);
            assertThat(String.valueOf(row.get("after_value"))).contains("6012345678901");
        }
    }

    // =================================================================
    // 수령
    // =================================================================

    @Nested
    @DisplayName("배송완료 처리")
    class Delivering {

        @Test
        @DisplayName("발송한 주문만 완료로 바꿀 수 있다")
        void requiresShippedFirst() throws Exception {
            long id = insertOrder("HS-DELIV1", "PAID", "김미발송");

            mockMvc.perform(post("/api/admin/orders/" + id + "/deliver")
                            .header("Authorization", bearer()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_NOT_SHIPPED"));
        }

        @Test
        @DisplayName("완료하면 수령 시각이 남고 주문도 배송완료가 된다")
        void recordsDeliveredAt() throws Exception {
            long id = insertOrder("HS-DELIV2", "PAID", "김수령");
            ship(id, "6012345678901");

            mockMvc.perform(post("/api/admin/orders/" + id + "/deliver")
                            .header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"))
                    .andExpect(jsonPath("$.deliveredAt").exists());

            assertThat(statusOf(id)).isEqualTo("DELIVERED");
        }

        @Test
        @DisplayName("완료된 주문의 송장번호는 바꿀 수 없다 — 이미 받은 물건의 번호를 고치는 것은 기록을 흐린다")
        void cannotChangeTrackingAfterDelivery() throws Exception {
            long id = insertOrder("HS-DELIV3", "PAID", "김완료");
            ship(id, "6012345678901");
            mockMvc.perform(post("/api/admin/orders/" + id + "/deliver")
                            .header("Authorization", bearer()))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/admin/orders/" + id + "/shipment")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("trackingNo", "6099999999999"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_ALREADY_DELIVERED"));
        }
    }

    // =================================================================
    // 고객 조회
    // =================================================================

    /**
     * 고객이 자기 주문의 배송을 보는 길.
     *
     * <p>여기서 가장 중요한 것은 <b>남의 주문번호가 404 라는 것</b>이다. 주문번호는
     * HS+날짜+순번이라 규칙이 단순해서, 소유자 검사가 없으면 숫자를 바꿔 가며 남의
     * 송장번호를 긁어 갈 수 있다.
     */
    @Nested
    @DisplayName("고객의 배송 조회")
    class CustomerView {

        @Test
        @DisplayName("발송 전에는 상태만 「발송 대기」로 온다 — 배송 행이 없는 것과 구분하지 않는다")
        void pendingBeforeShipping() throws Exception {
            insertOrder("HS-CUST1", "PAID", "김대기");

            mockMvc.perform(get("/api/v1/orders/HS-CUST1/shipment")
                            .header("Authorization", userBearer(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.trackingNo").doesNotExist());
        }

        @Test
        @DisplayName("발송하면 송장번호와 택배사 조회 주소가 보인다")
        void showsTrackingAfterShipping() throws Exception {
            long id = insertOrder("HS-CUST2", "PAID", "김조회");
            ship(id, "6012345678901");

            mockMvc.perform(get("/api/v1/orders/HS-CUST2/shipment")
                            .header("Authorization", userBearer(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SHIPPED"))
                    .andExpect(jsonPath("$.trackingNo").value("6012345678901"))
                    .andExpect(jsonPath("$.carrierName").value("우체국택배"))
                    .andExpect(jsonPath("$.trackingUrl").exists());
        }

        @Test
        @DisplayName("남의 주문번호는 404 다 — 403 은 그 번호의 주문이 있다는 것을 알려준다")
        void othersOrderIsNotFound() throws Exception {
            long id = insertOrder("HS-CUST3", "PAID", "김남의");
            ship(id, "6012345678901");

            long stranger = insertUser(900002L);

            mockMvc.perform(get("/api/v1/orders/HS-CUST3/shipment")
                            .header("Authorization", userBearer(stranger)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("로그인하지 않으면 배송을 볼 수 없다")
        void requiresLogin() throws Exception {
            insertOrder("HS-CUST4", "PAID", "김비로그인");

            mockMvc.perform(get("/api/v1/orders/HS-CUST4/shipment"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =================================================================
    // 권한
    // =================================================================

    @Test
    @DisplayName("OPERATOR 는 보기만 되고 발송 처리는 못 한다")
    void operatorCannotShip() throws Exception {
        long id = insertOrder("HS-ROLE", "PAID", "김권한");
        AdminUser operator = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "orderop", AdminRole.OPERATOR);
        String token = "Bearer " + login(operator.getLoginId());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/orders/" + id + "/shipment")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("trackingNo", "6012345678901"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하지 않으면 주문을 볼 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // 도우미
    // =================================================================

    private void ship(long orderId, String trackingNo) throws Exception {
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/shipment")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("trackingNo", trackingNo))))
                .andExpect(status().isOk());
    }

    private MvcResult search(String keyword) throws Exception {
        var request = get("/api/admin/orders").header("Authorization", bearer());
        if (keyword != null) {
            request = request.param("keyword", keyword);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private List<String> orderNosOf(MvcResult result) throws Exception {
        List<String> nos = new java.util.ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content")
                .forEach(node -> nos.add(node.get("orderNo").asText()));
        return nos;
    }

    private long insertUser() {
        return insertUser(900001L);
    }

    private long insertUser(long id) {
        Long created = jdbcTemplate.queryForObject(
                "INSERT INTO users (id, status) VALUES (?, 'ACTIVE') "
                        + "ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE' RETURNING id",
                Long.class, id);
        return created == null ? id : created;
    }

    /**
     * 회원 토큰을 가입 흐름 없이 직접 발급한다.
     *
     * <p>이 테스트가 보는 것은 배송이다. 본인확인·약관까지 통과하는 진짜 가입을 끼우면
     * 목 공급자 설정이 따라붙고, 배송 로직이 깨졌을 때 가입 쪽 실패와 구분하기 어려워진다.
     */
    private String userBearer(long id) {
        return "Bearer " + userJwtProvider.issueAccessToken(id);
    }

    private long insertOrder(String orderNo, String status, String receiver) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO orders (order_no, user_id, status, total_amount,
                                    receiver_name, receiver_phone, zipcode, address1)
                VALUES (?, ?, ?, 662300, ?, '01000000000', '06234', '서울 강남구')
                RETURNING id
                """, Long.class, orderNo, userId, status, receiver);
        return id == null ? 0 : id;
    }

    private void insertItem(long orderId, String productName) {
        jdbcTemplate.update("""
                INSERT INTO order_items (order_id, product_id, product_name,
                                         final_unit_price, quantity, final_amount)
                VALUES (?, 1, ?, 662300, 1, 662300)
                """, orderId, productName);
    }

    private String statusOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String trackingNoOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT tracking_no FROM shipments WHERE order_id = ?", String.class, orderId);
    }

    private String shippedAtOf(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT shipped_at::text FROM shipments WHERE order_id = ?", String.class, orderId);
    }

    private String login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId,
                                "password", AdminTestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + managerToken;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
