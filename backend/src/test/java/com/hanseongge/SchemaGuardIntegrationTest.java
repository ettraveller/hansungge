package com.hanseongge;

import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V8 이 실제로 막아주는지 확인한다 (개발계획 §0.2 결함 11건).
 *
 * <p>이 테스트가 검증하는 것은 <b>애플리케이션 코드가 아니라 스키마</b>다. 주문 스냅샷
 * 불변성·결제 멱등성·약관 동의 이력 같은 것들은 "서비스에서 조심하기"로는 지켜지지
 * 않는다 — 서비스는 늘어나고, 배치가 붙고, 운영자가 SQL 을 친다. 그래서 DB 가 거절하는지를
 * 직접 확인한다.
 *
 * <p>JPA 를 거치지 않고 {@link JdbcTemplate} 으로 raw SQL 을 날리는 것도 같은 이유다.
 * 엔티티를 통해 넣으면 엔티티의 검증을 확인하게 되지 스키마를 확인하는 것이 아니다.
 */
@SpringBootTest
class SchemaGuardIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired JdbcTemplate jdbc;

    /**
     * 이 테스트가 만든 데이터를 전부 걷어낸다.
     *
     * <p>임베디드 PostgreSQL 은 JVM 당 하나뿐이고 테스트 클래스가 <b>같은 데이터베이스를
     * 공유</b>한다. 남겨두면 다른 클래스의 {@code DELETE FROM products} 가 외래키 위반으로
     * 실패하거나, 목록 건수 단언이 어긋난다.
     *
     * <p>삭제 순서는 외래키를 거슬러 올라간다. 그리고 V8 이 건 불변 트리거들이 DELETE 를
     * 막으므로 잠시 내렸다가 다시 올린다 — <b>테스트에서만</b> 하는 일이고, 애플리케이션에는
     * 이 테이블들을 지우는 경로가 없다.
     */
    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void reset() {
        List<String> guarded = List.of(
                "order_items:trg_order_item_immutable",
                "order_item_price_components:trg_oipc_immutable",
                "user_agreements:trg_user_agreement_guard",
                "market_price_policy_history:trg_mpph_guard",
                "market_prices_effective:trg_mpe_guard");

        guarded.forEach(pair -> jdbc.execute(
                "ALTER TABLE " + pair.split(":")[0] + " DISABLE TRIGGER " + pair.split(":")[1]));
        try {
            for (String sql : List.of(
                    "DELETE FROM order_item_price_components",
                    "DELETE FROM order_item_adjustments",
                    "DELETE FROM order_status_histories",
                    "DELETE FROM order_items",
                    "DELETE FROM payment_transactions",
                    "DELETE FROM payments",
                    "DELETE FROM orders",
                    "DELETE FROM user_agreements",
                    "DELETE FROM users WHERE email = 'guard@test.local'",
                    "DELETE FROM product_price_components",
                    "DELETE FROM product_options",
                    "DELETE FROM products",
                    "DELETE FROM categories WHERE name = '테스트분류'",
                    "DELETE FROM market_price_policy_history",
                    "DELETE FROM market_prices_effective",
                    "DELETE FROM market_price_raw")) {
                jdbc.update(sql);
            }
            // 정책은 metal×side 당 1행인 시드 데이터라 지우지 않고 되돌린다.
            jdbc.update("""
                    UPDATE market_price_policies
                    SET mode = 'DIRECT', max_change_percent = NULL, derive_from = NULL, derive_ratio = NULL,
                        active = TRUE, updated_by = NULL
                    """);
        } finally {
            guarded.forEach(pair -> jdbc.execute(
                    "ALTER TABLE " + pair.split(":")[0] + " ENABLE TRIGGER " + pair.split(":")[1]));
        }
    }

    /**
     * 각 테스트가 자기 데이터를 직접 만든다. 순서 의존을 만들지 않기 위해서다.
     *
     * <p>이메일을 매번 다르게 만든다 — V13 이 {@code ux_users_email_active} 를 걸어서
     * 같은 주소로 두 번 넣을 수 없다. 한 테스트가 회원을 둘 만드는 경우가 실제로 있다.
     */
    private long insertUser() {
        return jdbc.queryForObject("""
                INSERT INTO users (email, name) VALUES (?, '테스트') RETURNING id
                """, Long.class, "guard-" + java.util.UUID.randomUUID() + "@test.local");
    }

    private long insertCategory() {
        return jdbc.queryForObject(
                "INSERT INTO categories (name, sort) VALUES ('테스트분류', 0) RETURNING id", Long.class);
    }

    private long insertProduct(long categoryId) {
        return jdbc.queryForObject("""
                INSERT INTO products (category_id, name, price_type, metal_type, weight_g)
                VALUES (?, '테스트상품', 'MARKET_PLUS', 'GOLD_24K', 3.75) RETURNING id
                """, Long.class, categoryId);
    }

    private long insertOrder(long userId) {
        return jdbc.queryForObject("""
                INSERT INTO orders (order_no, user_id, total_amount, receiver_name,
                                    receiver_phone, zipcode, address1)
                VALUES (?, ?, 100000, '받는이', '010-0000-0000', '00000', '주소')
                RETURNING id
                """, Long.class, "GUARD-" + System.nanoTime(), userId);
    }

    private long insertOrderItem(long orderId, long productId) {
        return jdbc.queryForObject("""
                INSERT INTO order_items (order_id, product_id, product_name,
                                         final_unit_price, quantity, final_amount)
                VALUES (?, ?, '테스트상품', 100000, 1, 100000) RETURNING id
                """, Long.class, orderId, productId);
    }

    // =================================================================
    // D-2 / §10.6 — 주문 스냅샷 불변
    // =================================================================

    @Nested
    @DisplayName("주문 스냅샷은 구조적으로 수정할 수 없다 (D-2)")
    class OrderSnapshot {

        @Test
        @DisplayName("order_items 를 UPDATE 하면 트리거가 거부한다 — 시세가 바뀌어도 주문가는 불변")
        void orderItemUpdateIsRejected() {
            long itemId = insertOrderItem(insertOrder(insertUser()), insertProduct(insertCategory()));

            assertThatThrownBy(() ->
                    jdbc.update("UPDATE order_items SET final_amount = 999999 WHERE id = ?", itemId))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immutable");

            // 값이 그대로인지까지 확인한다. 예외만 보고 넘어가면 부분 반영을 놓친다.
            assertThat(jdbc.queryForObject(
                    "SELECT final_amount FROM order_items WHERE id = ?", java.math.BigDecimal.class, itemId))
                    .isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("order_items 를 DELETE 하면 트리거가 거부한다")
        void orderItemDeleteIsRejected() {
            long itemId = insertOrderItem(insertOrder(insertUser()), insertProduct(insertCategory()));

            assertThatThrownBy(() -> jdbc.update("DELETE FROM order_items WHERE id = ?", itemId))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immutable");
        }

        @Test
        @DisplayName("구성요소 내역도 함께 잠긴다 — 합계만 잠그면 내역과 어긋난 주문이 만들어진다")
        void componentSnapshotIsAlsoImmutable() {
            long itemId = insertOrderItem(insertOrder(insertUser()), insertProduct(insertCategory()));
            jdbc.update("""
                    INSERT INTO order_item_price_components
                        (order_item_id, component_type, label, calc_method, amount)
                    VALUES (?, 'METAL_BASE', '순금 24K 시세', 'PER_GRAM_MARKET', 551250)
                    """, itemId);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE order_item_price_components SET amount = 1 WHERE order_item_id = ?", itemId))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immutable");
        }

        @Test
        @DisplayName("취소·환불은 원본을 고치지 않고 조정 행을 더한다")
        void adjustmentsAreAddedInsteadOfEditing() {
            long itemId = insertOrderItem(insertOrder(insertUser()), insertProduct(insertCategory()));

            jdbc.update("""
                    INSERT INTO order_item_adjustments
                        (order_item_id, adjustment_type, quantity, amount, reason, created_by)
                    VALUES (?, 'REFUND', 1, -100000, '고객 단순변심', 'admin')
                    """, itemId);

            // 실지급액 = 원본 + 조정합. 원본은 그대로 남아 있어야 한다.
            java.math.BigDecimal payable = jdbc.queryForObject("""
                    SELECT i.final_amount + COALESCE(SUM(a.amount), 0)
                    FROM order_items i
                    LEFT JOIN order_item_adjustments a ON a.order_item_id = i.id
                    WHERE i.id = ? GROUP BY i.final_amount
                    """, java.math.BigDecimal.class, itemId);

            assertThat(payable).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject(
                    "SELECT final_amount FROM order_items WHERE id = ?", java.math.BigDecimal.class, itemId))
                    .isEqualByComparingTo("100000");
        }
    }

    // =================================================================
    // D-6 — 결제 멱등성
    // =================================================================

    @Nested
    @DisplayName("결제 콜백은 중복 도착해도 두 번 반영되지 않는다 (D-6)")
    class PaymentIdempotency {

        private long insertPayment(String pgTid, String status) {
            long orderId = insertOrder(insertUser());
            return jdbc.queryForObject("""
                    INSERT INTO payments (order_id, provider, status, amount, pg_tid)
                    VALUES (?, 'TEST_PG', ?, 100000, ?) RETURNING id
                    """, Long.class, orderId, status, pgTid);
        }

        @Test
        @DisplayName("같은 멱등키로 거래를 두 번 기록할 수 없다")
        void duplicateIdempotencyKeyIsRejected() {
            long paymentId = insertPayment("TID-IDEM-1", "READY");
            String key = "TEST_PG:APPROVE:TID-IDEM-1";

            jdbc.update("""
                    INSERT INTO payment_transactions (payment_id, tx_type, success, idempotency_key)
                    VALUES (?, 'APPROVE', true, ?)
                    """, paymentId, key);

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO payment_transactions (payment_id, tx_type, success, idempotency_key)
                    VALUES (?, 'APPROVE', true, ?)
                    """, paymentId, key))
                    .isInstanceOf(DataAccessException.class);
        }

        @Test
        @DisplayName("멱등키가 없는 기록은 여러 건 남을 수 있다 — 부분 UNIQUE 라 NULL 은 제약 밖이다")
        void nullIdempotencyKeyIsNotConstrained() {
            long paymentId = insertPayment("TID-IDEM-2", "READY");

            for (int i = 0; i < 2; i++) {
                jdbc.update("""
                        INSERT INTO payment_transactions (payment_id, tx_type, success)
                        VALUES (?, 'WEBHOOK', true)
                        """, paymentId);
            }

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM payment_transactions WHERE payment_id = ?", Integer.class, paymentId))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("같은 PG 거래번호로 승인이 두 번 남을 수 없다")
        void duplicateApprovedTidIsRejected() {
            insertPayment("TID-APPROVED", "APPROVED");

            assertThatThrownBy(() -> insertPayment("TID-APPROVED", "APPROVED"))
                    .isInstanceOf(DataAccessException.class);
        }

        @Test
        @DisplayName("승인이 아닌 상태라면 같은 거래번호가 여러 건 있어도 된다 — 재시도 흔적이다")
        void sameTidIsAllowedWhenNotApproved() {
            insertPayment("TID-RETRY", "READY");
            insertPayment("TID-RETRY", "FAILED");

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM payments WHERE pg_tid = 'TID-RETRY'", Integer.class))
                    .isEqualTo(2);
        }
    }

    // =================================================================
    // D-7 — 기본 옵션 유일성
    // =================================================================

    @Test
    @DisplayName("상품당 기본 옵션은 하나뿐이다 — 재고가 두 곳에 존재할 수 없다 (D-7)")
    void onlyOneDefaultOptionPerProduct() {
        long productId = insertProduct(insertCategory());
        jdbc.update("""
                INSERT INTO product_options (product_id, name, stock, is_default)
                VALUES (?, '기본', 10, true)
                """, productId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product_options (product_id, name, stock, is_default)
                VALUES (?, '또다른 기본', 5, true)
                """, productId))
                .isInstanceOf(DataAccessException.class);

        // 기본이 아닌 옵션은 얼마든지 붙일 수 있다.
        jdbc.update("""
                INSERT INTO product_options (product_id, name, stock, is_default)
                VALUES (?, '7.5g (2돈)', 5, false)
                """, productId);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM product_options WHERE product_id = ?", Integer.class, productId))
                .isEqualTo(2);
    }

    // =================================================================
    // D-9 — 약관 동의 이력
    // =================================================================

    @Test
    @DisplayName("동의 철회는 UPDATE 가 아니라 새 행이다 — 언제 동의했고 언제 철회했는지 둘 다 남아야 한다 (D-9)")
    void agreementWithdrawalAddsRowInsteadOfUpdating() {
        long userId = insertUser();
        jdbc.update("""
                INSERT INTO user_agreements (user_id, agreement_type, version, agreed, ip)
                VALUES (?, 'MARKETING', 'v1.0', true, '203.0.113.1')
                """, userId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE user_agreements SET agreed = false WHERE user_id = ?", userId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        jdbc.update("""
                INSERT INTO user_agreements (user_id, agreement_type, version, agreed, ip)
                VALUES (?, 'MARKETING', 'v1.0', false, '203.0.113.1')
                """, userId);

        List<Boolean> history = jdbc.queryForList(
                "SELECT agreed FROM user_agreements WHERE user_id = ? ORDER BY agreed_at, id",
                Boolean.class, userId);
        assertThat(history).containsExactly(true, false);
    }

    // =================================================================
    // D-12 / D-4 / D-11 — 시세
    // =================================================================

    @Nested
    @DisplayName("시세 레이어 보호")
    class MarketPrice {

        private long manualSourceId() {
            return jdbc.queryForObject(
                    "SELECT id FROM market_price_sources WHERE code = 'MANUAL'", Long.class);
        }

        @Test
        @DisplayName("같은 소스·금속·기준시각의 원본은 한 번만 적재된다 (D-12)")
        void duplicateRawIsRejected() {
            long sourceId = manualSourceId();
            String quoteAt = "2026-08-27T09:00:00+09:00";

            jdbc.update("""
                    INSERT INTO market_price_raw (source_id, metal_type, price_per_gram, quote_at)
                    VALUES (?, 'GOLD_24K', 147000, ?::timestamptz)
                    """, sourceId, quoteAt);

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO market_price_raw (source_id, metal_type, price_per_gram, quote_at)
                    VALUES (?, 'GOLD_24K', 147000, ?::timestamptz)
                    """, sourceId, quoteAt))
                    .isInstanceOf(DataAccessException.class);

            // 기준시각이 다르면 정상 적재된다 — 막는 것은 "같은 시점의 반복 수집"뿐이다.
            jdbc.update("""
                    INSERT INTO market_price_raw (source_id, metal_type, price_per_gram, quote_at)
                    VALUES (?, 'GOLD_24K', 148000, '2026-08-27T10:00:00+09:00'::timestamptz)
                    """, sourceId);
        }

        @Test
        @DisplayName("원본에 FAILED 상태를 넣을 수 없다 (D-3)")
        void failedStatusIsNoLongerAllowed() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO market_price_raw (source_id, metal_type, price_per_gram, quote_at, status)
                    VALUES (?, 'SILVER', 1650, now(), 'FAILED')
                    """, manualSourceId()))
                    .isInstanceOf(DataAccessException.class);
        }

        @Test
        @DisplayName("보정 정책 이력은 고쳐 쓸 수 없다 (D-4)")
        void policyHistoryIsAppendOnly() {
            long policyId = jdbc.queryForObject(
                    "SELECT id FROM market_price_policies WHERE metal_type = 'GOLD_24K' AND price_side = 'SELL'",
                    Long.class);

            jdbc.update("""
                    INSERT INTO market_price_policy_history
                        (policy_id, metal_type, price_side, after_snapshot, change_reason, changed_by)
                    VALUES (?, 'GOLD_24K', 'SELL', '{"mode":"FIXED_ADJ","adjAmount":10000}'::jsonb,
                            '판매 마진 조정', 'admin')
                    """, policyId);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE market_price_policy_history SET change_reason = '수정' WHERE policy_id = ?",
                    policyId))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
        }

        @Test
        @DisplayName("효력 시세의 원본·보정 스냅샷도 변경이 막힌다 (D-11)")
        void effectiveSnapshotColumnsAreGuarded() {
            long sourceId = manualSourceId();
            long rawId = jdbc.queryForObject("""
                    INSERT INTO market_price_raw (source_id, metal_type, price_per_gram, quote_at)
                    VALUES (?, 'PLATINUM', 52000, '2026-08-27T11:00:00+09:00'::timestamptz) RETURNING id
                    """, Long.class, sourceId);

            long effectiveId = jdbc.queryForObject("""
                    INSERT INTO market_prices_effective
                        (metal_type, price_side, price_per_gram, base_raw_id, policy_mode,
                         policy_snapshot, change_reason, created_by,
                         source_code, raw_price_per_gram, adjustment_amount)
                    VALUES ('PLATINUM', 'SELL', 62000, ?, 'FIXED_ADJ', '{}'::jsonb, '테스트', 'admin',
                            'MANUAL', 52000, 10000)
                    RETURNING id
                    """, Long.class, rawId);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE market_prices_effective SET raw_price_per_gram = 1 WHERE id = ?", effectiveId))
                    .isInstanceOf(DataAccessException.class);

            // 마감(effective_to 채우기)은 여전히 허용된다 — 이것만이 유일하게 허용된 변경이다.
            jdbc.update("UPDATE market_prices_effective SET effective_to = now() WHERE id = ?", effectiveId);
            assertThat(jdbc.queryForObject(
                    "SELECT effective_to IS NOT NULL FROM market_prices_effective WHERE id = ?",
                    Boolean.class, effectiveId)).isTrue();
        }

        @Test
        @DisplayName("시세 방식은 직접 입력과 24K 환산만 넣을 수 있다 — 보정 방식은 V19 에서 닫혔다")
        void onlyDirectOrDerivedModes() {
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE market_price_policies SET mode = 'FIXED_ADJ' "
                            + "WHERE metal_type = 'GOLD_24K' AND price_side = 'SELL'"))
                    .isInstanceOf(DataAccessException.class);
        }

        @Test
        @DisplayName("24K 환산은 18K·14K 만, 24K 에서만, 1 이하 비율로만 된다")
        void derivationIsConstrained() {
            // 은은 순금 값에 비율로 이어지지 않는다
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE market_price_policies
                    SET mode = 'DERIVED', derive_from = 'GOLD_24K', derive_ratio = 0.5
                    WHERE metal_type = 'SILVER' AND price_side = 'SELL'
                    """)).isInstanceOf(DataAccessException.class);

            // 1 을 넘으면 18K 가 순금보다 비싸진다
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE market_price_policies
                    SET mode = 'DERIVED', derive_from = 'GOLD_24K', derive_ratio = 1.2
                    WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                    """)).isInstanceOf(DataAccessException.class);

            // 기준은 24K 뿐이다
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE market_price_policies
                    SET mode = 'DERIVED', derive_from = 'GOLD_14K', derive_ratio = 0.7
                    WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                    """)).isInstanceOf(DataAccessException.class);

            // 비율 없는 환산은 없다
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE market_price_policies
                    SET mode = 'DERIVED', derive_from = 'GOLD_24K', derive_ratio = NULL
                    WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                    """)).isInstanceOf(DataAccessException.class);

            // 올바른 설정은 들어간다
            jdbc.update("""
                    UPDATE market_price_policies
                    SET mode = 'DERIVED', derive_from = 'GOLD_24K', derive_ratio = 0.735
                    WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                    """);
        }

        @Test
        @DisplayName("승인 대기·수집 로그 테이블은 V19 에서 사라졌다")
        void retiredTablesAreGone() {
            Integer n = jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('market_price_pending', 'market_price_fetch_logs')
                    """, Integer.class);
            assertThat(n).isZero();
        }
    }

    // =================================================================
    // D-1 — 상품 구성요소
    // =================================================================

    @Nested
    @DisplayName("상품 가격 구성요소 (D-1)")
    class PriceComponents {

        @Test
        @DisplayName("주얼리 5종 구성요소를 등록할 수 있다 — labor_cost 단일 컬럼으로는 불가능했다")
        void jewelryComponentsCanBeStored() {
            long productId = insertProduct(insertCategory());

            jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, sort, component_type, label, calc_method, metal_type, weight_g)
                    VALUES (?, 0, 'METAL_BASE', '18K 금지금', 'PER_GRAM_MARKET', 'GOLD_18K', 5.2)
                    """, productId);
            jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, sort, component_type, label, calc_method, unit_amount)
                    VALUES (?, 1, 'LABOR', '세팅 공임', 'FIXED_AMOUNT', 62000)
                    """, productId);
            jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, sort, component_type, label, calc_method, unit_amount)
                    VALUES (?, 2, 'MATERIAL', '체인 부자재', 'FIXED_AMOUNT', 18000)
                    """, productId);
            jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, sort, component_type, label, calc_method, unit_amount)
                    VALUES (?, 3, 'STONE', '큐빅 3석', 'FIXED_AMOUNT', 24000)
                    """, productId);
            jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, sort, component_type, label, calc_method, rate)
                    VALUES (?, 4, 'MARGIN', '마진', 'PERCENT_OF_SUBTOTAL', 15)
                    """, productId);

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM product_price_components WHERE product_id = ?",
                    Integer.class, productId)).isEqualTo(5);
        }

        @Test
        @DisplayName("산출에 필요한 값이 빠진 구성요소는 저장되지 않는다 — 조회 시점에 계산이 실패하는 상품을 만들지 않는다")
        void incompleteComponentIsRejected() {
            long productId = insertProduct(insertCategory());

            // PER_GRAM_MARKET 인데 중량이 없다
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, component_type, label, calc_method, metal_type)
                    VALUES (?, 'METAL_BASE', '금지금', 'PER_GRAM_MARKET', 'GOLD_24K')
                    """, productId))
                    .isInstanceOf(DataAccessException.class);

            // PERCENT_OF_BASE 인데 비율이 없다
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_price_components
                        (product_id, component_type, label, calc_method)
                    VALUES (?, 'MARGIN', '마진', 'PERCENT_OF_BASE')
                    """, productId))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    // =================================================================
    // D-10 — 금속별 변동률 임계값
    // =================================================================

    @Test
    @DisplayName("금속별 변동률 임계값을 둘 수 있다 — 은의 5%와 금의 5%는 같은 사건이 아니다 (D-10)")
    void perMetalChangeThreshold() {
        jdbc.update("""
                UPDATE market_price_policies SET max_change_percent = 12
                WHERE metal_type = 'SILVER' AND price_side = 'SELL'
                """);

        assertThat(jdbc.queryForObject("""
                SELECT max_change_percent FROM market_price_policies
                WHERE metal_type = 'SILVER' AND price_side = 'SELL'
                """, java.math.BigDecimal.class)).isEqualByComparingTo("12");

        // 임계값은 양수여야 한다. 0 이면 모든 변동이 보류로 걸려 시세가 멈춘다.
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE market_price_policies SET max_change_percent = 0
                WHERE metal_type = 'SILVER' AND price_side = 'SELL'
                """)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("DERIVED 정책은 파생 원본과 비율이 함께 있어야 한다 (§12.5)")
    void derivedPolicyNeedsSourceAndRatio() {
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE market_price_policies SET mode = 'DERIVED'
                WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                """)).isInstanceOf(DataAccessException.class);

        jdbc.update("""
                UPDATE market_price_policies
                SET mode = 'DERIVED', derive_from = 'GOLD_24K', derive_ratio = 0.75
                WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                """);

        assertThat(jdbc.queryForObject("""
                SELECT derive_ratio FROM market_price_policies
                WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                """, java.math.BigDecimal.class)).isEqualByComparingTo("0.75");

        // 다른 테스트에 영향을 주지 않도록 직접 입력으로 되돌린다.
        jdbc.update("""
                UPDATE market_price_policies
                SET mode = 'DIRECT', derive_from = NULL, derive_ratio = NULL
                WHERE metal_type = 'GOLD_18K' AND price_side = 'SELL'
                """);
    }
}
