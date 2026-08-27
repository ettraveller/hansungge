-- =====================================================================
-- V4__order.sql : 장바구니 / 주문(가격 Snapshot) / 결제 / 배송
-- 원칙 (지시서 14~16, 45항):
--   1) 주문가격은 서버 재계산 값만 사용. FE 가격 불신
--   2) order_items = 주문 시점 가격 Snapshot. 시세 변경과 무관하게 불변
--   3) 결제 승인금액 == 주문 Snapshot 합계 검증 후 PAID 전이
-- =====================================================================

-- ---------- 장바구니 ----------
CREATE TABLE carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id           BIGSERIAL PRIMARY KEY,
    cart_id      BIGINT        NOT NULL REFERENCES carts(id),
    product_id   BIGINT        NOT NULL REFERENCES products(id),
    option_id    BIGINT        REFERENCES product_options(id),
    quantity     INT           NOT NULL CHECK (quantity > 0),
    price_at_add NUMERIC(15,2) NOT NULL,           -- 담은 시점 가격 (시세변동 비교용)
    added_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_cart_item UNIQUE (cart_id, product_id, option_id)
);

-- ---------- 주문 ----------
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    order_no         VARCHAR(30)   NOT NULL UNIQUE,  -- 예: HS20261014-000123
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ORDER_CREATED'
                     CHECK (status IN ('ORDER_CREATED','PAYMENT_PENDING','PAID',
                                       'PREPARING','SHIPPED','DELIVERED',
                                       'CANCEL_REQUESTED','CANCELLED',
                                       'REFUND_PENDING','REFUNDED')),
    total_amount     NUMERIC(15,2) NOT NULL,        -- items 합 + 배송비 - 할인 (서버 계산)
    shipping_fee     NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
    receiver_name    VARCHAR(100)  NOT NULL,
    receiver_phone   VARCHAR(30)   NOT NULL,
    zipcode          VARCHAR(10)   NOT NULL,
    address1         VARCHAR(200)  NOT NULL,
    address2         VARCHAR(200),
    order_memo       VARCHAR(300),
    ordered_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_user   ON orders(user_id, ordered_at DESC);
CREATE INDEX idx_order_status ON orders(status, ordered_at DESC);

-- ---------- 주문 항목 = 가격 Snapshot (지시서 15항 필드 전부) ----------
CREATE TABLE order_items (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT        NOT NULL REFERENCES orders(id),
    product_id         BIGINT        NOT NULL,     -- 참조용 (FK 미설정: 상품 삭제와 무관하게 보존)
    product_name       VARCHAR(200)  NOT NULL,
    option_name        VARCHAR(100),
    metal_type         VARCHAR(20),
    weight_g           NUMERIC(12,4),
    market_price       NUMERIC(15,2),              -- 적용 시세 (1g)
    adjustment         NUMERIC(15,2) NOT NULL DEFAULT 0,
    labor_cost         NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount           NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax                NUMERIC(15,2) NOT NULL DEFAULT 0,
    final_unit_price   NUMERIC(15,2) NOT NULL,
    quantity           INT           NOT NULL CHECK (quantity > 0),
    final_amount       NUMERIC(15,2) NOT NULL,     -- final_unit_price × quantity - discount
    effective_price_id BIGINT REFERENCES market_prices_effective(id)  -- 근거 시세
);
CREATE INDEX idx_oitem_order ON order_items(order_id);

-- ---------- 결제 ----------
CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES orders(id),
    provider    VARCHAR(20)   NOT NULL,            -- PG사 확정 후 값 고정 (TODO A-3)
    method      VARCHAR(20),                       -- CARD / NAVERPAY / KAKAOPAY (통합창 내 수단)
    status      VARCHAR(20)   NOT NULL DEFAULT 'READY'
                CHECK (status IN ('READY','APPROVED','FAILED','CANCELLED','PARTIAL_REFUNDED','REFUNDED')),
    amount      NUMERIC(15,2) NOT NULL,
    pg_tid      VARCHAR(100),
    approved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_order ON payments(order_id);
CREATE INDEX idx_pay_tid   ON payments(pg_tid);

CREATE TABLE payment_transactions (                 -- PG 요청/응답 원문 (append-only)
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT      NOT NULL REFERENCES payments(id),
    tx_type    VARCHAR(15) NOT NULL CHECK (tx_type IN ('READY','APPROVE','CANCEL','REFUND','WEBHOOK')),
    request    JSONB,
    response   JSONB,                               -- 카드번호 등 민감정보는 앱에서 마스킹 후 저장
    success    BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ptx_payment ON payment_transactions(payment_id, created_at);

-- ---------- 배송 ----------
CREATE TABLE shipments (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT      NOT NULL REFERENCES orders(id),
    carrier      VARCHAR(50),
    tracking_no  VARCHAR(60),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','SHIPPED','DELIVERED')),
    shipped_at   TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ship_order ON shipments(order_id);
