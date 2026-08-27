-- =====================================================================
-- V2__market_price.sql : 시세 시스템 (원본 / 보정정책 / 최종적용 / 승인대기)
-- 원칙:
--   1) 기준 단위는 KRW / 1g 고정
--   2) market_prices_effective 는 append-only. 가격 UPDATE는 트리거로 차단
--   3) 현재값 = effective_to IS NULL 인 행 (metal×side당 정확히 1개)
-- =====================================================================

-- ---------- 시세 소스 레지스트리 ----------
CREATE TABLE market_price_sources (
    id       BIGSERIAL PRIMARY KEY,
    code     VARCHAR(20)  NOT NULL UNIQUE,        -- MANUAL, KRX
    name     VARCHAR(100) NOT NULL,
    active   BOOLEAN      NOT NULL DEFAULT TRUE,
    priority INT          NOT NULL DEFAULT 100
);
INSERT INTO market_price_sources(code, name, priority) VALUES
 ('MANUAL', '관리자 수동 고시', 10),
 ('KRX',    'KRX 자동 수집',    20);   -- 약관 확인(TODO B-1) 전까지 수집기 비활성 운용

-- ---------- 수집/입력 원본 (append-only) ----------
CREATE TABLE market_price_raw (
    id             BIGSERIAL PRIMARY KEY,
    source_id      BIGINT        NOT NULL REFERENCES market_price_sources(id),
    metal_type     VARCHAR(20)   NOT NULL
                   CHECK (metal_type IN ('GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM')),
    price_per_gram NUMERIC(15,2) NOT NULL CHECK (price_per_gram > 0),
    collected_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status         VARCHAR(10)   NOT NULL DEFAULT 'NORMAL'
                   CHECK (status IN ('NORMAL','ANOMALY','FAILED')),
    raw_payload    JSONB,
    created_by     VARCHAR(60)                     -- MANUAL 입력 시 관리자 ID
);
CREATE INDEX idx_mpr_metal_time ON market_price_raw(metal_type, collected_at DESC);

-- ---------- 관리자 보정 정책 (metal × side 당 1행, 현재값) ----------
CREATE TABLE market_price_policies (
    id                 BIGSERIAL PRIMARY KEY,
    metal_type         VARCHAR(20)  NOT NULL
                       CHECK (metal_type IN ('GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM')),
    price_side         VARCHAR(4)   NOT NULL CHECK (price_side IN ('SELL','BUY')),
    mode               VARCHAR(15)  NOT NULL DEFAULT 'MANUAL'
                       CHECK (mode IN ('AUTO','FIXED_ADJ','PERCENT_ADJ','MANUAL')),
    adj_amount         NUMERIC(15,2) NOT NULL DEFAULT 0,   -- FIXED_ADJ: 원본 + 금액
    adj_percent        NUMERIC(7,4)  NOT NULL DEFAULT 0,   -- PERCENT_ADJ: 원본 × (1+%/100)
    manual_price       NUMERIC(15,2),                       -- MANUAL: 최종가 직접 지정
    updated_by         VARCHAR(60),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_policy UNIQUE (metal_type, price_side)
);
-- 초기 정책: 전 금속 SELL/BUY = MANUAL (시세 미입력 → 앱은 "시세문의" 표시)
INSERT INTO market_price_policies(metal_type, price_side)
SELECT m, s FROM unnest(ARRAY['GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM']) m,
            unnest(ARRAY['SELL','BUY']) s;

-- ---------- 최종 적용 시세 (append-only = 변경 이력 겸용) ----------
CREATE TABLE market_prices_effective (
    id              BIGSERIAL PRIMARY KEY,
    metal_type      VARCHAR(20)   NOT NULL
                    CHECK (metal_type IN ('GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM')),
    price_side      VARCHAR(4)    NOT NULL CHECK (price_side IN ('SELL','BUY')),
    price_per_gram  NUMERIC(15,2) NOT NULL CHECK (price_per_gram > 0),
    base_raw_id     BIGINT        REFERENCES market_price_raw(id),
    policy_mode     VARCHAR(15)   NOT NULL,
    policy_snapshot JSONB         NOT NULL,        -- 적용 당시 정책 전체
    effective_from  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    effective_to    TIMESTAMPTZ,                   -- NULL = 현재 적용값
    change_reason   VARCHAR(300)  NOT NULL,
    created_by      VARCHAR(60)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- metal×side당 "현재값" 1개 강제
CREATE UNIQUE INDEX ux_mpe_current
    ON market_prices_effective(metal_type, price_side)
    WHERE effective_to IS NULL;
CREATE INDEX idx_mpe_history ON market_prices_effective(metal_type, price_side, effective_from DESC);

-- append-only 강제: 허용되는 UPDATE는 effective_to 마감(NULL→값) 단 1가지.
-- 가격/금속/기간 등 다른 컬럼 변경과 DELETE는 DB 레벨에서 차단한다.
CREATE OR REPLACE FUNCTION fn_mpe_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'market_prices_effective is append-only (DELETE forbidden)';
    END IF;
    IF NEW.price_per_gram  IS DISTINCT FROM OLD.price_per_gram
       OR NEW.metal_type   IS DISTINCT FROM OLD.metal_type
       OR NEW.price_side   IS DISTINCT FROM OLD.price_side
       OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
       OR NEW.policy_snapshot IS DISTINCT FROM OLD.policy_snapshot
       OR OLD.effective_to IS NOT NULL THEN
        RAISE EXCEPTION 'market_prices_effective: only closing effective_to (NULL -> value) is allowed';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mpe_guard
    BEFORE UPDATE OR DELETE ON market_prices_effective
    FOR EACH ROW EXECUTE FUNCTION fn_mpe_guard();

-- ---------- 이상 변동 승인 대기 ----------
CREATE TABLE market_price_pending (
    id             BIGSERIAL PRIMARY KEY,
    raw_id         BIGINT        NOT NULL REFERENCES market_price_raw(id),
    metal_type     VARCHAR(20)   NOT NULL,
    price_side     VARCHAR(4)    NOT NULL,
    calc_price     NUMERIC(15,2) NOT NULL,        -- 정책 적용 결과(적용 보류된 값)
    prev_price     NUMERIC(15,2),
    change_percent NUMERIC(8,4),
    status         VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    decided_by     VARCHAR(60),
    decided_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_mpp_status ON market_price_pending(status, created_at DESC);
