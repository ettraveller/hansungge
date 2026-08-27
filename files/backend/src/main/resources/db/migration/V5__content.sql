-- =====================================================================
-- V5__content.sql : 공지 / 뉴스 / FAQ / QnA / 쿠폰 / 이벤트 / 지점
-- =====================================================================

CREATE TABLE notices (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    pinned     BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by VARCHAR(60),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE news (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    content       TEXT,
    source_url    VARCHAR(500),                    -- 외부기사는 링크만 (본문 복제 금지)
    thumbnail_key VARCHAR(300),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    VARCHAR(60),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE faqs (
    id       BIGSERIAL PRIMARY KEY,
    category VARCHAR(50)  NOT NULL,               -- 주문/배송/매입/회원 등
    question VARCHAR(300) NOT NULL,
    answer   TEXT         NOT NULL,
    sort     INT          NOT NULL DEFAULT 0,
    active   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE qnas (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    product_id  BIGINT       REFERENCES products(id),
    title       VARCHAR(200) NOT NULL,
    question    TEXT         NOT NULL,
    secret      BOOLEAN      NOT NULL DEFAULT FALSE,
    answer      TEXT,
    answered_by VARCHAR(60),
    answered_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_qna_user ON qnas(user_id, created_at DESC);

CREATE TABLE coupons (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(40)   UNIQUE,
    name           VARCHAR(100)  NOT NULL,
    discount_type  VARCHAR(10)   NOT NULL CHECK (discount_type IN ('AMOUNT','PERCENT')),
    discount_value NUMERIC(15,2) NOT NULL CHECK (discount_value > 0),
    min_order      NUMERIC(15,2) NOT NULL DEFAULT 0,
    max_discount   NUMERIC(15,2),
    valid_from     TIMESTAMPTZ   NOT NULL,
    valid_to       TIMESTAMPTZ   NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE coupon_issues (
    id        BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT      NOT NULL REFERENCES coupons(id),
    user_id   BIGINT      NOT NULL REFERENCES users(id),
    used      BOOLEAN     NOT NULL DEFAULT FALSE,
    used_at   TIMESTAMPTZ,
    order_id  BIGINT      REFERENCES orders(id),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_coupon_user UNIQUE (coupon_id, user_id)
);

CREATE TABLE events (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    content       TEXT,
    thumbnail_key VARCHAR(300),
    start_at      TIMESTAMPTZ  NOT NULL,
    end_at        TIMESTAMPTZ  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE stores (                              -- 지점안내
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    phone      VARCHAR(30),
    address    VARCHAR(300),
    hours      VARCHAR(200),
    map_lat    NUMERIC(10,7),
    map_lng    NUMERIC(10,7),
    sort       INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);
