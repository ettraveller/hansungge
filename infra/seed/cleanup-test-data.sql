-- =====================================================================
-- 테스트 상품 · 더미 시세 정리 (2026-10-29)
--
-- 오픈 준비를 위해 개발 중 쌓인 값을 걷어낸다. 상품은 상태만 바꾸는 것이
-- 아니라 행을 지운다 — 관리자 상품관리 목록에서 사라진다.
--
-- 지우는 것
--   · 상품  아래 :products 에 적은 id 와 그 자식들 (물리삭제)
--   · 시세  dummy-seed 10,980건 + hsgmaster 의 "test" 사유 16건
--   · 수집원본  2026-10-29 이전 8건
--
-- 남기는 것
--   · 시세  hsgmaster2 가 2026-10-29 14:43 에 고시한 값 — 지금 적용 중이다.
--           이것까지 지우면 상품 가격이 잡히지 않아 주문이 막힌다.
--   · 수집원본 9·10번 — 위 고시의 근거다.
--   · 주문 10건 — order_items 는 주문 당시의 상품을 복사해 두므로 상품을
--                 지워도 남는다. 거래 기록이라 지우지 않는다
--                 (전자상거래법 §6 — 5년 보존).
--
-- ---------------------------------------------------------------------
-- 실행
--
--   ⚠️ 파일을 /tmp 에 둬야 한다. postgres 계정은 /home/hanseongge 를 읽을
--      수 없다 (drwxr-x---) — 홈에 두면 "Permission denied" 가 난다.
--
--   개발 PC:  scp infra/seed/cleanup-test-data.sql \
--               hanseongge@49.50.129.220:/tmp/
--   서버:     sudo -u postgres psql -d hanseongge \
--               -v products='1,2,3' -f /tmp/cleanup-test-data.sql
--
--   sudo 가 비밀번호를 묻는다.
--
-- ⚠️ 지울 상품 id 는 -v products= 로 넘긴다. 지금 목록이 무엇인지 먼저 본다:
--
--   sudo -u postgres psql -d hanseongge \
--     -c "SELECT id, name, status, created_at FROM products ORDER BY id"
--
--   id 를 넘기지 않으면 1,2,3 만 지운다 (처음 확인한 TEST · testst · ㅇㅇ).
--   테스트하면서 상품을 더 만들었으면 그 id 까지 함께 적을 것.
--
-- ⚠️ 먼저 백업할 것:
--   sudo -u postgres pg_dump -d hanseongge \
--     -t market_prices_effective -t market_price_raw -t products \
--     -t product_options -t product_images -t product_price_components \
--     -t cart_items > /tmp/before-cleanup.sql
-- =====================================================================

\set ON_ERROR_STOP on

-- -v 로 넘기지 않았을 때의 기본값. psql 은 없는 변수를 조용히 빈 문자열로
-- 바꿔 버린다.
\if :{?products}
\else
  \set products '1,2,3'
\endif

\echo '지울 상품 id:' :products

-- 목록을 임시 표로 한 번만 푼다.
--
-- ⚠️ `IN (:products)` 로 쓰면 안 된다. 빈 값을 넘겼을 때 `IN ()` 이 되어
-- 문법 오류로 멈춘다 — "상품은 이미 지웠고 시세만 지우고 싶다"가 흔한 상황인데
-- 그때마다 없는 id(-1)를 지어내 넘겨야 했다 (2026-10-30 실제로 걸렸다).
--
-- 문자열로 받아 쪼개면 빈 값은 그냥 0건이 된다. 덤으로 공백과 꼬리 쉼표
-- ("1, 2," 같은 입력)도 함께 걸러진다.
CREATE TEMP TABLE target_products AS
SELECT NULLIF(btrim(x), '')::bigint AS id
  FROM unnest(string_to_array(:'products', ',')) AS x
 WHERE NULLIF(btrim(x), '') IS NOT NULL;

-- 무엇을 지우는지 먼저 보여준다. 트랜잭션 밖이라 이 출력은 COMMIT 과
-- 무관하게 화면에 남는다 — 지운 뒤에 "무엇을 지웠지?" 를 묻지 않게 된다.
SELECT p.id, p.name, p.status, p.created_at
  FROM products p
  JOIN target_products t ON t.id = p.id
 ORDER BY p.id;

BEGIN;

-- ── 1. 테스트 상품 ──────────────────────────────────────────────────
--
-- 순서가 중요하다. 장바구니는 상품뿐 아니라 담을 때의 시세도 참조한다
-- (cart_items.effective_price_id_at_add). 시세를 먼저 지우면 여기서 막힌다.
--
-- 상품의 자식 FK 는 대부분 ON DELETE 제한이라 손으로 순서대로 지운다.
-- wishlist_items · recently_viewed 만 CASCADE 라 따라 지워진다.
--
-- cart_items 는 조건 없이 비운다. 실제 고객이 아직 없고, 지울 상품을 담은
-- 줄만 골라 지우면 "담을 때의 시세"가 지워진 다른 줄이 남아 조회가 깨진다.
DELETE FROM cart_items;
DELETE FROM product_price_components WHERE product_id IN (SELECT id FROM target_products);
DELETE FROM product_images           WHERE product_id IN (SELECT id FROM target_products);
DELETE FROM product_options          WHERE product_id IN (SELECT id FROM target_products);
DELETE FROM qnas                     WHERE product_id IN (SELECT id FROM target_products);
DELETE FROM products                 WHERE id IN (SELECT id FROM target_products);

-- ── 2. 더미 · 테스트 시세 ───────────────────────────────────────────
--
-- market_prices_effective 는 append-only 다 — 트리거가 DELETE 를 막는다.
-- 운영에서 내리는 것은 평소에 하지 않는 일이라, 한 트랜잭션 안에서 끝내
-- 그 사이에 다른 고시가 끼어들지 못하게 한다.
ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard;

-- ⚠️ 주문이 가리키는 시세는 지우지 않는다 (order_items.effective_price_id).
--
-- 그 행은 "이 주문에 어떤 고시가 적용됐는가"의 근거다. 금액을 다투게 되면
-- 주문서의 숫자가 아니라 이 행을 보고 따진다. 더미 데이터라도 주문이 걸려
-- 있으면 그 주문의 근거라서 남겨야 한다.
--
-- 날짜나 작성자만 보고 지우면 FK 에서 막혀 전체가 롤백된다 — 2026-10-30 에
-- 실제로 걸렸다 (Key (id)=(21972) is still referenced from table "order_items").
--
-- cart_items 도 같은 컬럼을 참조하지만 위에서 이미 비웠다.
DELETE FROM market_prices_effective e
 WHERE (e.created_by = 'dummy-seed'
        OR (e.created_by = 'hsgmaster' AND e.change_reason LIKE '%test%'))
   AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.effective_price_id = e.id)
   AND NOT EXISTS (SELECT 1 FROM cart_items ci WHERE ci.effective_price_id_at_add = e.id);

ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard;

-- 수집원본은 고시가 참조한다 (market_prices_effective.base_raw_id). 날짜만 보고
-- 지우면 살아 있는 고시의 근거를 지우려다 FK 에서 막혀 전체가 롤백된다 —
-- 2026-10-29 리허설에서 실제로 걸렸다.
--
-- 아무도 참조하지 않는 것만 지운다. 남는 행이 있어도 그것은 지금 적용 중인
-- 시세의 근거라서 남아야 하는 행이다.
DELETE FROM market_price_raw r
 WHERE r.collected_at < '2026-10-29'
   AND NOT EXISTS (
       SELECT 1 FROM market_prices_effective e WHERE e.base_raw_id = r.id);

-- ── 3. 확인 ─────────────────────────────────────────────────────────
--
-- hsgmaster2 가 남아 있어야 한다. 비어 있으면 적용 중인 시세를 함께 지운
-- 것이므로 COMMIT 하지 말고 ROLLBACK 할 것.
-- dummy-seed 가 0 이 아니어도 괜찮다. 주문이 가리키는 행은 남겨 두기 때문이다
-- (아래 "주문이 붙잡은 시세" 가 그 수다).
SELECT created_by, count(*) AS 남은_시세, max(effective_from) AS 최근고시
  FROM market_prices_effective GROUP BY created_by ORDER BY created_by;
SELECT count(DISTINCT effective_price_id) AS 주문이_붙잡은_시세
  FROM order_items WHERE effective_price_id IS NOT NULL;
SELECT count(*) AS 남은_수집원본 FROM market_price_raw;
SELECT count(*) AS 남은_상품 FROM products;
SELECT count(*) AS 주문_그대로 FROM orders;

COMMIT;
