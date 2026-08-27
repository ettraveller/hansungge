-- =====================================================================
-- V3__product.sql : 카테고리 / 상품 / 옵션 / 이미지
-- 원칙 (지시서 40항):
--   상품 테이블에 "현재 판매가"를 저장하지 않는다.
--   가격 = 시세(effective) × 중량 + 공임 (PriceCalculationService 계산)
--   fixed_price 는 FIXED/MANUAL 타입 전용이다.
-- =====================================================================

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    parent_id  BIGINT REFERENCES categories(id),
    name       VARCHAR(100) NOT NULL,
    sort       INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_cat_parent ON categories(parent_id, sort);

-- 초기 카테고리(참고사이트 구조 기반, 관리자 화면에서 자유 수정)
INSERT INTO categories(name, sort) VALUES
 ('골드바', 10), ('실버바', 20), ('돌선물', 30),
 ('순금기념품', 40), ('순금주얼리', 50), ('기타제품', 60);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    category_id   BIGINT        NOT NULL REFERENCES categories(id),
    name          VARCHAR(200)  NOT NULL,
    description   TEXT,
    thumbnail_key VARCHAR(300),                    -- Object Storage key (URL 미저장)
    metal_type    VARCHAR(20)
                  CHECK (metal_type IN ('GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM')),
    price_type    VARCHAR(20)   NOT NULL
                  CHECK (price_type IN ('FIXED','MARKET_LINKED','MARKET_PLUS','MARKET_MULTIPLIER','MANUAL')),
    weight_g      NUMERIC(12,4) CHECK (weight_g IS NULL OR weight_g > 0),
    purity        NUMERIC(6,4),                    -- 0.9999, 0.7500 등
    labor_cost    NUMERIC(15,2) NOT NULL DEFAULT 0,
    multiplier    NUMERIC(8,4)  NOT NULL DEFAULT 1,-- MARKET_MULTIPLIER 전용
    fixed_price   NUMERIC(15,2),                   -- FIXED/MANUAL 전용
    status        VARCHAR(20)   NOT NULL DEFAULT 'ON_SALE'
                  CHECK (status IN ('ON_SALE','SOLD_OUT','HIDDEN','DISCONTINUED')),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    VARCHAR(60),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by    VARCHAR(60),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- 타입별 필수값 정합성
    CONSTRAINT chk_price_type_fields CHECK (
        (price_type IN ('FIXED','MANUAL')    AND fixed_price IS NOT NULL)
     OR (price_type NOT IN ('FIXED','MANUAL') AND metal_type IS NOT NULL AND weight_g IS NOT NULL)
    )
);
CREATE INDEX idx_prod_category ON products(category_id, status, active);
CREATE INDEX idx_prod_name     ON products(name);

CREATE TABLE product_options (
    id                    BIGSERIAL PRIMARY KEY,
    product_id            BIGINT        NOT NULL REFERENCES products(id),
    name                  VARCHAR(100)  NOT NULL,  -- "3.75g (1돈)" 등 표시용
    weight_g              NUMERIC(12,4) CHECK (weight_g IS NULL OR weight_g > 0),
    additional_labor_cost NUMERIC(15,2) NOT NULL DEFAULT 0,
    stock                 INT           NOT NULL DEFAULT 0 CHECK (stock >= 0),
    sort                  INT           NOT NULL DEFAULT 0,
    active                BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_opt_product ON product_options(product_id, sort);

CREATE TABLE product_images (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products(id),
    object_key VARCHAR(300) NOT NULL,
    sort       INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_img_product ON product_images(product_id, sort);
