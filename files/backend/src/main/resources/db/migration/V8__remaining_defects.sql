-- =====================================================================
-- V8 — 잔여 스키마 결함 11건 해소 (개발계획 §0.2 / TODO C-1)
--
-- V7 이 D-3(수집 실패 기록) 하나만 떼어 해소했고, 나머지 11건이 남아 있었다.
-- 이 파일이 그 전부를 처리한다:
--   D-1  product_price_components        주얼리 구성요소 가격
--   D-2  order_item_price_components     주문 구성요소 스냅샷
--   D-4  market_price_policy_history     보정 정책 변경 이력
--   D-5  (확인) MANUAL 고시도 raw 경유    — 코드에서 이미 해소, 여기선 스냅샷 컬럼만
--   D-6  payment_transactions.idempotency_key
--   D-7  product_options.is_default      기본 옵션 유일성
--   D-8  order_items.option_id
--   D-9  user_agreements                 약관 동의 이력
--   D-10 market_price_policies.max_change_percent
--   D-11 market_prices_effective 원본·보정 스냅샷 컬럼
--   D-12 market_price_raw 중복 적재 방지
--
-- 왜 지금인가 — 주문·결제·회원 코드보다 먼저여야 한다
--   이 중 D-2·D-6·D-9 는 나중에 붙일 수 없다. 주문이 한 건이라도 쌓인 뒤
--   구성요소 스냅샷을 추가하면 그 주문은 내역을 영영 복원할 수 없고, 멱등키
--   없이 받은 결제 콜백은 중복 여부를 사후 판정할 수 없으며, 약관 동의 시각은
--   소급 생성 자체가 위법이다. 스키마가 먼저 서 있어야 코드가 안전하다.
--
-- 적용 시점의 데이터 (2026-08-27): raw 22 / effective 22 / pending 0 /
--   products 7 / product_options 8 / orders 0 / order_items 0 / users 0.
--   주문·회원이 비어 있어 불변 트리거와 NOT NULL 승격을 지금 걸 수 있다.
--   이 시점을 놓치면 백필 없이는 불가능해진다.
-- =====================================================================


-- =====================================================================
-- 1. 시세 (§12.2)
-- =====================================================================

-- ---------- L1. market_price_raw ----------

-- D-3 잔여: status 에서 FAILED 제거. 실패는 V7 의 fetch_logs 가 받는다.
-- 값이 남아 있으면 "가격 없는 가격 행"이 다시 생길 여지가 남는다.
ALTER TABLE market_price_raw DROP CONSTRAINT market_price_raw_status_check;
ALTER TABLE market_price_raw ADD CONSTRAINT market_price_raw_status_check
      CHECK (status IN ('NORMAL', 'ANOMALY'));

-- 시세 기준시각과 수집시각의 분리.
-- KRX 일별 데이터는 "언제의 시세인가"와 "언제 가져왔는가"가 다르다. 한 컬럼으로
-- 뭉개면 장 마감가를 밤에 수집했을 때 중복 판정이 어긋난다.
--
-- DEFAULT now() 를 준 이유: 이 컬럼은 NOT NULL 인데, 기본값이 없으면 아직
-- quote_at 을 모르는 기존 INSERT 경로가 전부 깨진다. 수동 고시는 "고시하는
-- 순간이 곧 기준시각"이라 now() 가 옳은 값이기도 하다.
ALTER TABLE market_price_raw ADD COLUMN quote_at TIMESTAMPTZ;
UPDATE market_price_raw SET quote_at = collected_at WHERE quote_at IS NULL;
ALTER TABLE market_price_raw ALTER COLUMN quote_at SET NOT NULL;
ALTER TABLE market_price_raw ALTER COLUMN quote_at SET DEFAULT now();

-- D-12: 같은 소스의 같은 금속·같은 기준시각은 한 번만 적재한다.
-- 없으면 스케줄러가 동일 시세를 매 주기 무한 적재한다.
CREATE UNIQUE INDEX ux_mpr_dedup ON market_price_raw (source_id, metal_type, quote_at);

COMMENT ON COLUMN market_price_raw.quote_at IS '시세 기준 시각. collected_at(수집 시각)과 다를 수 있다';


-- ---------- L2. market_price_policies ----------

-- D-10: 금속별 변동률 임계값. NULL 이면 전역 설정값(MAX_PRICE_CHANGE_PERCENT)을 쓴다.
-- 은은 하루 5% 가 흔하고 금은 드물다 — 하나의 전역값으로는 둘 다 맞출 수 없다.
ALTER TABLE market_price_policies ADD COLUMN max_change_percent NUMERIC(5, 2)
      CHECK (max_change_percent IS NULL OR max_change_percent > 0);

-- 파생 시세 (§12.5). 18K/14K 를 24K 에서 산출하기 위한 자리다.
-- ⚠️ 계산 로직은 아직 없다. 파생 비율은 TODO A-7 로 미결정 상태이며,
--    확정 전에 mode='DERIVED' 를 넣으면 애플리케이션이 읽지 못한다.
--    API 는 enum 검증이 있어 이 값을 만들 수 없고, 직접 SQL 로만 가능하다 — 하지 말 것.
ALTER TABLE market_price_policies ADD COLUMN derive_from  VARCHAR(20)
      CHECK (derive_from IS NULL OR derive_from IN ('GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM'));
ALTER TABLE market_price_policies ADD COLUMN derive_ratio NUMERIC(6, 4)
      CHECK (derive_ratio IS NULL OR derive_ratio > 0);

-- auto_apply=false 면 자동 수집이 들어와도 발행하지 않고 항상 승인을 거친다.
-- 오픈 초기 안정화 기간용 안전장치다.
ALTER TABLE market_price_policies ADD COLUMN auto_apply BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE market_price_policies ADD COLUMN active     BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE market_price_policies DROP CONSTRAINT market_price_policies_mode_check;
ALTER TABLE market_price_policies ADD CONSTRAINT market_price_policies_mode_check
      CHECK (mode IN ('AUTO', 'FIXED_ADJ', 'PERCENT_ADJ', 'MANUAL', 'DERIVED'));

-- DERIVED 를 고르면 파생 원본과 비율이 반드시 있어야 한다.
ALTER TABLE market_price_policies ADD CONSTRAINT chk_policy_derived
      CHECK (mode <> 'DERIVED' OR (derive_from IS NOT NULL AND derive_ratio IS NOT NULL));


-- D-4: 보정 정책 변경 이력.
-- policies 는 metal×side 당 1행을 UPDATE 하는 구조라 "언제 얼마에서 얼마로 왜"가
-- 남지 않는다. audit_logs 에 흔적은 있지만 시세 도메인 조회 화면을 만들 수 없어
-- 별도 테이블로 둔다.
CREATE TABLE market_price_policy_history (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       BIGINT       NOT NULL REFERENCES market_price_policies (id),
    metal_type      VARCHAR(20)  NOT NULL,
    price_side      VARCHAR(4)   NOT NULL,
    before_snapshot JSONB,                          -- 최초 등록이면 NULL
    after_snapshot  JSONB        NOT NULL,
    change_reason   VARCHAR(300) NOT NULL,
    changed_by      VARCHAR(60)  NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_mpph_policy ON market_price_policy_history (policy_id, changed_at DESC);

-- 이력은 고쳐 쓰지 않는다. 시세 효력·주문 스냅샷과 같은 원칙이다.
CREATE OR REPLACE FUNCTION fn_mpph_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'market_price_policy_history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mpph_guard
    BEFORE UPDATE OR DELETE ON market_price_policy_history
    FOR EACH ROW EXECUTE FUNCTION fn_mpph_guard();


-- ---------- L3. market_prices_effective (D-11) ----------

-- 원본가·보정액을 효력 행에 직접 복사해 둔다.
-- 지금은 base_raw_id 조인 + policy_snapshot 파싱으로 3값을 만들고 있는데,
-- 조인 대상이 사라지거나 정책 스냅샷 형식이 바뀌면 과거 가격의 근거가 흐려진다.
-- "원본 200,000 / 보정 +10,000 / 최종 210,000"이 한 행에서 읽혀야 한다.
--
-- ※ 백필하려면 append-only 가드를 잠시 내려야 한다. 가드가 effective_to 가 이미
--    닫힌 행의 UPDATE 를 전부 막기 때문이다. 백필 후 신규 컬럼까지 포함해 다시 건다.
DROP TRIGGER trg_mpe_guard ON market_prices_effective;

ALTER TABLE market_prices_effective ADD COLUMN source_code        VARCHAR(20);
ALTER TABLE market_prices_effective ADD COLUMN raw_price_per_gram NUMERIC(15, 2);
ALTER TABLE market_prices_effective ADD COLUMN adjustment_amount  NUMERIC(15, 2);
ALTER TABLE market_prices_effective ADD COLUMN pending_id         BIGINT
      REFERENCES market_price_pending (id);
ALTER TABLE market_prices_effective ADD COLUMN quote_at           TIMESTAMPTZ;

UPDATE market_prices_effective e
SET raw_price_per_gram = r.price_per_gram,
    quote_at           = r.quote_at,
    source_code        = s.code
FROM market_price_raw r
         JOIN market_price_sources s ON s.id = r.source_id
WHERE e.base_raw_id = r.id;

-- 보정액은 정의상 (최종 - 원본)이다. 정책 모드와 무관하게 성립한다.
UPDATE market_prices_effective
SET adjustment_amount = price_per_gram - raw_price_per_gram
WHERE raw_price_per_gram IS NOT NULL;

-- 가드 재생성: 신규 스냅샷 컬럼도 변경 불가 대상에 넣는다.
-- 이 컬럼들이야말로 과거 가격의 근거라, 본체보다 덜 지켜서는 의미가 없다.
CREATE OR REPLACE FUNCTION fn_mpe_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'market_prices_effective is append-only (DELETE forbidden)';
    END IF;
    IF NEW.price_per_gram      IS DISTINCT FROM OLD.price_per_gram
       OR NEW.metal_type       IS DISTINCT FROM OLD.metal_type
       OR NEW.price_side       IS DISTINCT FROM OLD.price_side
       OR NEW.effective_from   IS DISTINCT FROM OLD.effective_from
       OR NEW.policy_snapshot  IS DISTINCT FROM OLD.policy_snapshot
       OR NEW.raw_price_per_gram IS DISTINCT FROM OLD.raw_price_per_gram
       OR NEW.adjustment_amount  IS DISTINCT FROM OLD.adjustment_amount
       OR NEW.source_code      IS DISTINCT FROM OLD.source_code
       OR NEW.quote_at         IS DISTINCT FROM OLD.quote_at
       OR NEW.pending_id       IS DISTINCT FROM OLD.pending_id
       OR OLD.effective_to     IS NOT NULL THEN
        RAISE EXCEPTION 'market_prices_effective: only closing effective_to (NULL -> value) is allowed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mpe_guard
    BEFORE UPDATE OR DELETE ON market_prices_effective
    FOR EACH ROW EXECUTE FUNCTION fn_mpe_guard();


-- ---------- L4. market_price_pending ----------

-- 승인은 보류 이후에 이뤄지고, 그 사이 정책이 바뀔 수 있다. 보류 당시 정책을
-- 함께 저장해야 승인 결과가 예측 가능하다.
ALTER TABLE market_price_pending ADD COLUMN raw_price_per_gram NUMERIC(15, 2);
ALTER TABLE market_price_pending ADD COLUMN policy_snapshot    JSONB;
ALTER TABLE market_price_pending ADD COLUMN reject_reason      VARCHAR(300);
ALTER TABLE market_price_pending ADD COLUMN expires_at         TIMESTAMPTZ;

-- metal×side 당 PENDING 은 1건만. 같은 조합이 여러 건 쌓이면 어느 것을 승인해야
-- 하는지 알 수 없고, 알림도 중복된다.
CREATE UNIQUE INDEX ux_mpp_pending ON market_price_pending (metal_type, price_side)
    WHERE status = 'PENDING';


-- =====================================================================
-- 2. 상품 (§9, §13.2)
-- =====================================================================

-- pricing_model: 가격을 어디서 만드는가.
--   SIMPLE    price_type + labor_cost           (골드바 등)
--   COMPONENT product_price_components 를 순서대로 (주얼리 등)
-- 기존 7개 상품은 전부 SIMPLE 이므로 DEFAULT 로 그대로 흡수된다.
ALTER TABLE products ADD COLUMN pricing_model VARCHAR(12) NOT NULL DEFAULT 'SIMPLE'
      CHECK (pricing_model IN ('SIMPLE', 'COMPONENT'));

-- SEO 용 URL 조각. 한글 상품명에서 자동 생성하면 의미가 뭉개져 비워 둔다 —
-- 운영에서 상품별로 지정한다. NULL 은 /shop/product/{id} 로 동작한다.
ALTER TABLE products ADD COLUMN slug          VARCHAR(200) UNIQUE;
ALTER TABLE products ADD COLUMN summary       VARCHAR(300);
ALTER TABLE products ADD COLUMN min_qty       INT NOT NULL DEFAULT 1 CHECK (min_qty >= 1);
ALTER TABLE products ADD COLUMN max_qty       INT CHECK (max_qty IS NULL OR max_qty >= min_qty);
ALTER TABLE products ADD COLUMN sale_start_at TIMESTAMPTZ;
ALTER TABLE products ADD COLUMN sale_end_at   TIMESTAMPTZ;
ALTER TABLE products ADD COLUMN external_ref  VARCHAR(60);   -- ERP 연계 대비 (§1.3)
ALTER TABLE products ADD COLUMN sort          INT NOT NULL DEFAULT 0;

ALTER TABLE products ADD CONSTRAINT chk_product_sale_period
      CHECK (sale_start_at IS NULL OR sale_end_at IS NULL OR sale_end_at > sale_start_at);

COMMENT ON COLUMN products.purity IS '표시 전용. 어떤 계산식에도 넣지 않는다 — metal_type 이 이미 순도를 내포해 곱하면 이중 적용된다';


-- ---------- product_options (D-7) ----------

ALTER TABLE product_options ADD COLUMN metal_type VARCHAR(20)
      CHECK (metal_type IS NULL OR metal_type IN ('GOLD_24K','GOLD_18K','GOLD_14K','SILVER','PLATINUM'));
ALTER TABLE product_options ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product_options ADD COLUMN sku        VARCHAR(60);

-- 기존 상품의 기본 옵션 지정: 정렬 최상단(동률이면 id 최소) 1건.
-- 이미 서비스가 상품 등록 시 기본 옵션을 만들고 있어, 이 백필은 그때 만들어진
-- 첫 옵션을 그대로 가리킨다.
UPDATE product_options o
SET is_default = TRUE
WHERE o.id = (SELECT x.id
              FROM product_options x
              WHERE x.product_id = o.product_id
              ORDER BY x.sort, x.id
              LIMIT 1);

-- 상품당 기본 옵션은 하나뿐이다. "옵션 없는 상품" 개념을 없애 재고 소스를
-- product_options.stock 한 곳으로 고정한 것이 D-7 의 핵심이다.
CREATE UNIQUE INDEX ux_opt_default ON product_options (product_id) WHERE is_default;


-- ---------- product_price_components (D-1) ----------

-- 주얼리는 "금 + 공임 + 부자재 + 스톤 + 마진"으로 구성된다. labor_cost 단일
-- 컬럼으로는 표현할 수 없고, 합계만 저장하면 고객 문의·환불 산정에서 내역을
-- 설명하지 못한다.
CREATE TABLE product_price_components (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT       NOT NULL REFERENCES products (id),
    option_id      BIGINT       REFERENCES product_options (id),   -- NULL = 상품 공통
    sort           INT          NOT NULL DEFAULT 0,                -- 계산 순서
    component_type VARCHAR(20)  NOT NULL
                   CHECK (component_type IN ('METAL_BASE', 'LABOR', 'MATERIAL',
                                             'STONE', 'MARGIN', 'PACKAGING', 'DISCOUNT')),
    label          VARCHAR(100) NOT NULL,                          -- 고객에게 보이는 문구
    calc_method    VARCHAR(20)  NOT NULL
                   CHECK (calc_method IN ('PER_GRAM_MARKET', 'PER_GRAM_FIXED',
                                          'FIXED_AMOUNT', 'PERCENT_OF_BASE',
                                          'PERCENT_OF_SUBTOTAL', 'MULTIPLIER_OF_BASE')),
    metal_type     VARCHAR(20),
    price_side     VARCHAR(4)   NOT NULL DEFAULT 'SELL' CHECK (price_side IN ('SELL', 'BUY')),
    weight_g       NUMERIC(12, 4) CHECK (weight_g IS NULL OR weight_g > 0),
    unit_amount    NUMERIC(15, 2),
    rate           NUMERIC(9, 4),
    taxable        BOOLEAN      NOT NULL DEFAULT TRUE,
    visible        BOOLEAN      NOT NULL DEFAULT TRUE,   -- 상세페이지 내역 노출 여부
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 산출 방식마다 필요한 값이 다르다. 여기서 막지 않으면 "금액이 안 나오는
    -- 구성요소"가 저장되고, 그 상품은 조회 시점에야 계산에 실패한다.
    CONSTRAINT chk_ppc_method CHECK (
        (calc_method = 'PER_GRAM_MARKET' AND metal_type IS NOT NULL AND weight_g IS NOT NULL)
     OR (calc_method = 'PER_GRAM_FIXED'  AND unit_amount IS NOT NULL AND weight_g IS NOT NULL)
     OR (calc_method = 'FIXED_AMOUNT'    AND unit_amount IS NOT NULL)
     OR (calc_method IN ('PERCENT_OF_BASE', 'PERCENT_OF_SUBTOTAL', 'MULTIPLIER_OF_BASE')
         AND rate IS NOT NULL)
    )
);
CREATE INDEX idx_ppc_product ON product_price_components (product_id, option_id, sort);


-- =====================================================================
-- 3. 주문 (§10)
-- =====================================================================

-- 주문 항목은 계산 결과를 값으로 복사해 보관한다. 상품·시세를 조인해 가격을
-- 다시 만들지 않는다 — 14:00 주문이 14:05 시세 변경에 흔들리지 않는 근거다.
ALTER TABLE order_items ADD COLUMN option_id                 BIGINT;   -- D-8
ALTER TABLE order_items ADD COLUMN category_name             VARCHAR(100);
ALTER TABLE order_items ADD COLUMN price_type                VARCHAR(20);
ALTER TABLE order_items ADD COLUMN pricing_model             VARCHAR(12);
ALTER TABLE order_items ADD COLUMN price_side                VARCHAR(4) DEFAULT 'SELL';
ALTER TABLE order_items ADD COLUMN purity                    NUMERIC(6, 4);
ALTER TABLE order_items ADD COLUMN raw_market_price          NUMERIC(15, 2);
ALTER TABLE order_items ADD COLUMN market_adjustment         NUMERIC(15, 2);
ALTER TABLE order_items ADD COLUMN unit_price_before_discount NUMERIC(15, 2);
ALTER TABLE order_items ADD COLUMN vat_mode                  VARCHAR(12);
ALTER TABLE order_items ADD COLUMN rounding_policy           VARCHAR(30);
ALTER TABLE order_items ADD COLUMN calc_version              VARCHAR(10);
ALTER TABLE order_items ADD COLUMN calculated_at             TIMESTAMPTZ;
ALTER TABLE order_items ADD COLUMN calc_snapshot             JSONB;

COMMENT ON COLUMN order_items.effective_price_id IS 'FK 는 감사 추적용. 불변성의 실제 보증은 market_price 등 값 복사 컬럼이다';


-- D-2: 구성요소 스냅샷.
-- 합계만 남기면 "이 목걸이 660,000원이 어떻게 나온 값인가"에 답할 수 없다.
-- 고객 문의·환불 산정·세금계산서에서 반드시 필요하다.
CREATE TABLE order_item_price_components (
    id             BIGSERIAL PRIMARY KEY,
    order_item_id  BIGINT        NOT NULL REFERENCES order_items (id),
    sort           INT           NOT NULL DEFAULT 0,
    component_type VARCHAR(20)   NOT NULL
                   CHECK (component_type IN ('METAL_BASE', 'LABOR', 'MATERIAL',
                                             'STONE', 'MARGIN', 'PACKAGING', 'DISCOUNT')),
    label          VARCHAR(100)  NOT NULL,
    calc_method    VARCHAR(20)   NOT NULL,
    metal_type     VARCHAR(20),
    weight_g       NUMERIC(12, 4),
    unit_price     NUMERIC(15, 2),
    rate           NUMERIC(9, 4),
    amount         NUMERIC(15, 2) NOT NULL,        -- 이 구성요소의 확정 금액
    taxable        BOOLEAN        NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_oipc_item ON order_item_price_components (order_item_id, sort);

-- 불변식 SUM(amount) = order_items.unit_price_before_discount 는 애플리케이션과
-- 테스트에서 검증한다. DB 제약으로 걸면 행 단위 INSERT 중간 상태에서 항상 깨진다.


-- D-2 보조: 취소·환불은 원본을 고치지 않고 조정 행을 더한다.
-- 실지급액 = order_items.final_amount + SUM(adjustments.amount)
CREATE TABLE order_item_adjustments (
    id              BIGSERIAL PRIMARY KEY,
    order_item_id   BIGINT        NOT NULL REFERENCES order_items (id),
    adjustment_type VARCHAR(20)   NOT NULL
                    CHECK (adjustment_type IN ('CANCEL', 'REFUND', 'PARTIAL_REFUND', 'COMPENSATION')),
    quantity        INT           CHECK (quantity IS NULL OR quantity > 0),
    amount          NUMERIC(15, 2) NOT NULL,       -- 음수 = 차감
    reason          VARCHAR(300)  NOT NULL,
    created_by      VARCHAR(60)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_oia_item ON order_item_adjustments (order_item_id, created_at DESC);


-- 주문 상태 전이 기록. enum 의 canTransitTo() 가 전이를 막고, 통과한 전이는
-- 전부 여기 남는다.
CREATE TABLE order_status_histories (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders (id),
    from_status VARCHAR(20),                        -- 최초 생성이면 NULL
    to_status   VARCHAR(20) NOT NULL,
    reason      VARCHAR(300),
    changed_by  VARCHAR(60) NOT NULL,               -- 관리자 ID 또는 SYSTEM
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_osh_order ON order_status_histories (order_id, changed_at DESC);


-- ---------- Snapshot 불변 트리거 (§10.6) ----------
--
-- "조심해서 UPDATE 하지 않기"가 아니라 구조적으로 불가능하게 만든다.
-- 지금 order_items 가 비어 있어 예외 없이 걸 수 있다. 주문이 쌓인 뒤에는
-- 기존 운영 경로가 UPDATE 를 쓰고 있을지 알 수 없어 이만큼 단호하게 걸기 어렵다.
CREATE OR REPLACE FUNCTION fn_order_item_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'order_items is immutable (snapshot). Use order_item_adjustments.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_item_immutable
    BEFORE UPDATE OR DELETE ON order_items
    FOR EACH ROW EXECUTE FUNCTION fn_order_item_immutable();

-- 구성요소 스냅샷도 같은 원칙이다. 본체만 잠그고 내역을 열어두면 합계와
-- 내역이 어긋난 주문이 만들어진다.
CREATE OR REPLACE FUNCTION fn_oipc_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'order_item_price_components is immutable (snapshot)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_oipc_immutable
    BEFORE UPDATE OR DELETE ON order_item_price_components
    FOR EACH ROW EXECUTE FUNCTION fn_oipc_immutable();


-- =====================================================================
-- 4. 결제 (§11.3) — D-6
-- =====================================================================

-- PG 콜백은 네트워크 재시도로 중복 도착하는 것이 정상 동작이다.
-- 멱등키가 없으면 이중 승인·이중 환불을 막을 방법이 애플리케이션 락뿐인데,
-- 그것은 인스턴스가 늘어나는 순간 깨진다. UNIQUE 제약이 최종 방어선이다.
ALTER TABLE payment_transactions ADD COLUMN idempotency_key VARCHAR(120);

CREATE UNIQUE INDEX ux_ptx_idem ON payment_transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 같은 PG 거래번호로 승인이 두 번 남을 수 없다.
CREATE UNIQUE INDEX ux_pay_tid_approved ON payments (pg_tid)
    WHERE pg_tid IS NOT NULL AND status = 'APPROVED';

COMMENT ON COLUMN payment_transactions.idempotency_key IS 'provider + tx_type + pg_tid. 중복 콜백은 UNIQUE 위반으로 차단하고 기존 결과를 200 으로 반환한다';


-- =====================================================================
-- 5. 회원 (§7.3) — D-9
-- =====================================================================

-- 전자상거래법·개인정보보호법상 필수다. 동의 시각은 소급 생성이 불가능하므로
-- 회원가입 기능보다 반드시 먼저 존재해야 한다.
CREATE TABLE user_agreements (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    agreement_type VARCHAR(30) NOT NULL
                   CHECK (agreement_type IN ('TERMS', 'PRIVACY', 'MARKETING', 'AGE_14', 'THIRD_PARTY')),
    version        VARCHAR(20) NOT NULL,           -- 동의 시점의 약관 버전
    agreed         BOOLEAN     NOT NULL,
    agreed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip             VARCHAR(45)
);
CREATE INDEX idx_agree_user ON user_agreements (user_id, agreement_type, agreed_at DESC);

-- 철회(마케팅 수신거부)도 agreed=false 행을 추가한다. UPDATE 하지 않는다 —
-- "언제 동의했고 언제 철회했는가"가 둘 다 남아야 증명이 된다.
CREATE OR REPLACE FUNCTION fn_user_agreement_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'user_agreements is append-only. Insert a new row to withdraw consent.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_agreement_guard
    BEFORE UPDATE OR DELETE ON user_agreements
    FOR EACH ROW EXECUTE FUNCTION fn_user_agreement_guard();


-- =====================================================================
-- 6. 가격 계산 감사 (§13.7)
-- =====================================================================

-- 주문 시점 계산의 입력과 출력을 통째로 남긴다. 분쟁이 생겼을 때
-- "그때 어떤 시세와 어떤 정책으로 이 금액이 나왔는가"를 재현하는 근거다.
-- context='VIEW' 는 저장하지 않는다 (트래픽 대비 무의미한 적재).
CREATE TABLE price_calculation_logs (
    id                 BIGSERIAL PRIMARY KEY,
    context            VARCHAR(20) NOT NULL
                       CHECK (context IN ('VIEW', 'CART', 'ORDER', 'ADMIN_PREVIEW')),
    product_id         BIGINT      NOT NULL,
    option_id          BIGINT,
    effective_price_id BIGINT,
    calc_input         JSONB       NOT NULL,
    calc_output        JSONB       NOT NULL,
    calc_version       VARCHAR(10) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pcl_product ON price_calculation_logs (product_id, created_at DESC);
CREATE INDEX idx_pcl_created ON price_calculation_logs (created_at DESC);
