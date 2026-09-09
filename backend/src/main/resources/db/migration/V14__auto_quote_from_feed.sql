-- =====================================================================
-- V14 — 자동고시: 외부 피드에서 살때/팔때를 <b>각각</b> 받는다
--
-- 지금까지 L1 원본(market_price_raw)은 금속당 가격이 하나였다. 매수/매도는
-- L2 정책이 그 하나에서 갈라 만들었다. 그 구조는 "기준가 하나 + 우리 마진"
-- 모델에서는 옳지만, 발주 요구는 다르다 —
--
--   외부 고시의 <b>살 때와 팔 때를 각각 기본값으로 가져오고</b>,
--   거기에 각각 +/- 보정을 얹는다.
--
-- 그러려면 원본이 어느 쪽 값인지 알아야 한다. 그래서 price_side 를 더한다.
--
-- NULL 을 허용하는 이유 — 기존 행과 수동 고시 때문이다. 지금까지 쌓인 원본은
-- "양측 공통 기준가"라는 뜻이었고, 그 의미를 사후에 SELL 이나 BUY 로 바꿔
-- 적으면 거짓말이 된다. NULL = 양측 공통, 값이 있으면 그 쪽 전용이다.
-- 계산기는 "그 side 전용 원본이 있으면 그것을, 없으면 공통 원본을" 쓴다.
-- =====================================================================

-- ---------- L1. 원본에 매수/매도 구분 ----------

ALTER TABLE market_price_raw ADD COLUMN price_side VARCHAR(4)
      CHECK (price_side IS NULL OR price_side IN ('SELL', 'BUY'));

COMMENT ON COLUMN market_price_raw.price_side IS
    'NULL = 양측 공통 기준가(기존 의미). SELL/BUY = 그 쪽 전용 원본';

-- 중복 적재 방지 인덱스를 다시 만든다.
-- price_side 가 빠진 채로 두면 같은 기준시각의 살때·팔때 두 행이 서로를
-- 중복으로 밀어내, 둘 중 하나만 저장된다.
--
-- NULLS NOT DISTINCT 가 필요한 이유 — 기본적으로 PostgreSQL 은 NULL 을
-- 서로 다른 값으로 보아, 공통 기준가(price_side IS NULL) 행끼리는 중복
-- 판정이 되지 않는다. 그러면 D-12 에서 막았던 무한 적재가 되살아난다.
DROP INDEX IF EXISTS ux_mpr_dedup;
CREATE UNIQUE INDEX ux_mpr_dedup
    ON market_price_raw (source_id, metal_type, price_side, quote_at)
    NULLS NOT DISTINCT;

-- 계산기가 매번 "이 금속·이 side 의 최신 원본"을 찾는다. 그 조회의 인덱스다.
CREATE INDEX idx_mpr_metal_side_time
    ON market_price_raw (metal_type, price_side, quote_at DESC);


-- ---------- L2. 자동고시 / 수동고시 스위치 ----------

-- 지금까지 최종 시세를 발행하는 경로는 <b>관리자 수동 고시 하나뿐</b>이었다.
-- 수집기는 원본만 쌓고 발행은 하지 않았다. 자동고시는 그 빈자리를 메운다.
--
-- 이 스위치를 정책 행에 두는 이유 — 정책은 이미 (금속 × 매수/매도)로 나뉘어 있고
-- 보정값도 여기 있다. "얼마를 더할까"와 "그것을 자동으로 낼까"는 같이 결정되는
-- 사안이라, 떨어뜨려 두면 한쪽만 바꾸고 다른 쪽을 잊는 사고가 난다.
--
-- 기본값 FALSE — 켜는 것은 사람의 결정이어야 한다. 마이그레이션이 도는 순간
-- 외부 값이 판매가로 나가기 시작하면 안 된다.
ALTER TABLE market_price_policies
    ADD COLUMN auto_quote BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN market_price_policies.auto_quote IS
    'TRUE = 자동고시(수집값에 보정을 적용해 즉시 발행). FALSE = 수동고시(관리자가 직접 고시)';


-- ---------- 수집 원천 등록 ----------

-- priority 30 — MANUAL(10) 과 KRX(20) 다음이다. 사람이 고시한 값이 언제나
-- 외부 피드를 이긴다는 순서를 숫자로 고정해 둔다.
--
-- active=false 로 넣는다. 행이 있다고 수집이 시작되면 안 된다 —
-- 애플리케이션 설정(app.market-price.koreagoldx.enabled)과 이 행이 둘 다
-- 켜져야 돈다. KRX 때와 같은 2중 차단이고, 이유도 같다: 설정 하나를 실수로
-- 건드려 외부 요청이 나가기 시작하는 것을 막는다.
INSERT INTO market_price_sources(code, name, active, priority) VALUES
 ('KGX', '외부 고시 피드 (자동고시 기준값)', FALSE, 30)
ON CONFLICT (code) DO NOTHING;


-- ---------- 운영 설정 ----------

-- 10분 간격. 기존값은 30분이었다.
-- 정규장(09:00~15:30) 앞뒤를 여유 있게 잡아 10~18시로 둔다.
--
-- 이미 값을 바꿔 둔 운영 환경을 덮어쓰지 않는다. 관리자가 조정한 주기를
-- 마이그레이션이 되돌리면, 왜 주기가 돌아갔는지 아무도 설명하지 못한다.
UPDATE system_settings
   SET value = '0 0/10 10-18 * * MON-FRI',
       description = '시세 자동수집 스케줄 (10분 간격)',
       updated_at = now()
 WHERE key = 'MARKET_FETCH_CRON'
   AND value = '0 0/30 10-18 * * MON-FRI';

-- 시세 연동을 허용할 금속. 여기에 없는 금속의 상품은 price_type 이
-- MARKET_* 이어도 시세를 따르지 않고 "시세문의"로 남는다.
--
-- 상품마다 price_type 이 이미 있는데 왜 또 두는가 — 그것은 상품 한 건의
-- 설정이고, 이것은 <b>전체 차단기</b>다. 상품 200건의 price_type 을 일일이
-- 되돌리지 않고도 "지금은 순금만 연동한다"를 한 줄로 정할 수 있어야 한다.
-- 은·백금을 열 때 재배포가 필요 없다는 뜻이기도 하다.
INSERT INTO system_settings(key, value, description) VALUES
 ('PRICE_LINKED_METALS', 'GOLD_24K',
  '시세 연동을 허용할 금속 (쉼표 구분). 비우면 전체 차단. 예: GOLD_24K,GOLD_18K')
ON CONFLICT (key) DO NOTHING;

-- 외부 피드 값의 단위. 한국 귀금속 고시는 관행상 1돈(3.75g) 단위다.
-- 코드에 3.75 를 박지 않는 이유 — 공급자가 바뀌면 이 값도 바뀌는데,
-- 그때 재배포를 하게 만들면 시세가 멈춘 채로 배포를 기다리게 된다.
INSERT INTO system_settings(key, value, description) VALUES
 ('FEED_UNIT_GRAMS', '3.75',
  '외부 피드 가격 1건이 몇 그램 기준인가. 1돈=3.75g')
ON CONFLICT (key) DO NOTHING;
