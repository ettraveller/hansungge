-- =====================================================================
-- V19 — 시세는 관리자만 고시한다
--
-- 외부 시세를 자동으로 받아 오지 않기로 했다(2026-09-15). 그러면 아래 네 가지가
-- 전부 설 자리를 잃는다. 모두 "밖에서 들어오는 값은 믿을 수 없다"는 전제로 만든
-- 장치이기 때문이다.
--
--   · 보정      — 받아온 값에 얼마를 더하거나 뺄까
--   · 자동고시  — 받아온 값을 사람 손 없이 바로 낼까
--   · 승인 대기 — 받아온 값이 튀면 발행을 멈추고 사람을 기다린다
--   · 수집 로그 — 받아오기가 실패했나
--
-- 남는 것:
--   · 관리자가 입력한 값이 곧 고시가다                    → mode = DIRECT
--   · 18K·14K 는 24K 고시가에 비율을 곱해 낼 수 있다     → mode = DERIVED
--   · 크게 바뀌는 값은 고시 전에 한 번 더 확인받는다     → 화면의 확인창 (저장 구조 없음)
--
-- ⚠️ 과거 시세 행(market_prices_effective)의 값은 건드리지 않는다. 그 행들의
--    policy_mode 에는 FIXED_ADJ 같은 옛 값이 그대로 남는다 — 그때 그렇게 계산됐다는
--    사실이고, 과거 주문 금액의 근거다.
-- =====================================================================


-- ---------- L2. 정책: 직접 입력 / 24K 환산 ----------

ALTER TABLE market_price_policies DROP CONSTRAINT market_price_policies_mode_check;
ALTER TABLE market_price_policies DROP CONSTRAINT chk_policy_derived;

-- 지금까지의 설정은 전부 "보정"이었다. 받아올 값이 없으니 더할 대상도 없다.
-- 모두 직접 입력으로 되돌린다. 환산은 관리자가 비율을 정해 켜는 것이지
-- 마이그레이션이 대신 켜지 않는다.
UPDATE market_price_policies
   SET mode = 'DIRECT',
       derive_from = NULL,
       derive_ratio = NULL,
       updated_at = now();

ALTER TABLE market_price_policies ALTER COLUMN mode SET DEFAULT 'DIRECT';
ALTER TABLE market_price_policies ADD CONSTRAINT market_price_policies_mode_check
      CHECK (mode IN ('DIRECT', 'DERIVED'));

-- 환산은 18K·14K 만, 24K 에서만, 1 이하 비율로만 된다.
-- 은·백금은 금이 아니라 순금 값에 비율로 이어지지 않는다. 비율이 1 을 넘으면
-- 18K 가 순금보다 비싸진다 — 오타(7.35) 말고는 그런 값이 들어올 이유가 없다.
--
-- 직접 입력으로 돌려도 derive_from·derive_ratio 는 남겨 둘 수 있다. 다시 환산으로
-- 켤 때 화면이 직전 비율을 채워 주기 위해서다.
ALTER TABLE market_price_policies ADD CONSTRAINT chk_policy_derived
      CHECK (mode <> 'DERIVED'
             OR (metal_type IN ('GOLD_18K', 'GOLD_14K')
                 AND derive_from = 'GOLD_24K'
                 AND derive_ratio IS NOT NULL
                 AND derive_ratio <= 1));

ALTER TABLE market_price_policies DROP COLUMN auto_quote;
ALTER TABLE market_price_policies DROP COLUMN auto_apply;
ALTER TABLE market_price_policies DROP COLUMN adj_amount;
ALTER TABLE market_price_policies DROP COLUMN adj_percent;
ALTER TABLE market_price_policies DROP COLUMN manual_price;


-- ---------- L3. 효력 시세: 승인 대기와의 연결을 끊는다 ----------

-- pending_id 는 애플리케이션이 한 번도 채운 적이 없다(엔티티에 매핑되지 않았다).
-- 그래도 가드 함수가 이 컬럼을 이름으로 비교하고 있어, 함수를 먼저 내리고 다시 건다.
DROP TRIGGER trg_mpe_guard ON market_prices_effective;

ALTER TABLE market_prices_effective DROP COLUMN pending_id;

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
       OR OLD.effective_to     IS NOT NULL THEN
        RAISE EXCEPTION 'market_prices_effective: only closing effective_to (NULL -> value) is allowed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mpe_guard
    BEFORE UPDATE OR DELETE ON market_prices_effective
    FOR EACH ROW EXECUTE FUNCTION fn_mpe_guard();


-- ---------- L4. 승인 대기 / 수집 로그 ----------

-- 승인 대기는 저장 구조가 아니라 확인창으로 바뀐다. 사람이 입력한 값을 같은 사람이
-- 다시 승인하는 절차는 확인 한 번과 다르지 않고, 그 사이 시세만 멈춰 있게 된다.
DROP TABLE market_price_pending;

DROP TABLE market_price_fetch_logs;
DROP FUNCTION fn_mpfl_guard();


-- ---------- 원천 ----------

-- 원본 행이 이 원천들을 참조할 수 있어 지우지 않는다. 쓰지 않는다는 것만 못 박는다.
UPDATE market_price_sources SET active = FALSE WHERE code <> 'MANUAL';


-- ---------- 운영 설정 ----------

DELETE FROM system_settings
 WHERE key IN ('MARKET_FETCH_CRON', 'FEED_UNIT_GRAMS', 'PRICE_LINKED_METALS', 'PRICE_STALE_MINUTES');

UPDATE system_settings
   SET description = '직전 고시보다 이 비율(%)을 넘게 바뀌는 값은 고시하기 전에 한 번 더 확인을 받는다.',
       updated_at = now()
 WHERE key = 'MAX_PRICE_CHANGE_PERCENT';
