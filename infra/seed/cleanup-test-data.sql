-- =====================================================================
-- 테스트 상품 · 더미 시세 정리 (2026-10-29)
--
-- 오픈 준비를 위해 개발 중 쌓인 값을 걷어낸다.
--
-- 지우는 것
--   · 시세  dummy-seed 10,980건 + hsgmaster 의 "test" 사유 16건
--   · 수집원본  2026-10-29 이전 8건
--   · 상품  id 1(TEST) · 2(testst) · 3(ㅇㅇ) 과 그 자식들
--
-- 남기는 것
--   · 시세  hsgmaster2 가 2026-10-29 14:43 에 고시한 값 — 지금 적용 중이다.
--           이것까지 지우면 상품 가격이 잡히지 않아 주문이 막힌다.
--   · 수집원본 9·10번 — 위 고시의 근거다.
--   · 주문 10건 — order_items 는 상품을 복사해 가지므로 상품을 지워도 남는다.
--                 거래 기록이라 지우지 않는다(전자상거래법 5년 보존).
--
-- 실행
--   sudo -u postgres psql -d hanseongge -f cleanup-test-data.sql
--
-- ⚠️ 먼저 백업할 것:
--   sudo -u postgres pg_dump -d hanseongge \
--     -t market_prices_effective -t market_price_raw -t products \
--     -t product_options -t product_images -t product_price_components \
--     -t cart_items > before-cleanup.sql
-- =====================================================================

\set ON_ERROR_STOP on
BEGIN;

-- ── 1. 테스트 상품 ──────────────────────────────────────────────────
--
-- 순서가 중요하다. 장바구니는 상품뿐 아니라 <b>담을 때의 시세</b>도 참조한다
-- (cart_items.effective_price_id_at_add). 시세를 먼저 지우면 여기서 막힌다.
--
-- 상품의 자식 FK 는 전부 ON DELETE 제한(a)이라 손으로 순서대로 지운다.
-- wishlist_items · recently_viewed 만 CASCADE 라 따라 지워진다.
DELETE FROM cart_items;
DELETE FROM product_price_components WHERE product_id IN (1, 2, 3);
DELETE FROM product_images           WHERE product_id IN (1, 2, 3);
DELETE FROM product_options          WHERE product_id IN (1, 2, 3);
DELETE FROM qnas                     WHERE product_id IN (1, 2, 3);
DELETE FROM products                 WHERE id IN (1, 2, 3);

-- ── 2. 더미 · 테스트 시세 ───────────────────────────────────────────
--
-- market_prices_effective 는 append-only 다. 트리거가 DELETE 를 막는다.
-- 운영에서 내리는 것은 평소에 하지 않는 일이라, 한 트랜잭션 안에서 끝내
-- 그 사이에 다른 고시가 끼어들지 못하게 한다.
ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard;

DELETE FROM market_prices_effective
 WHERE created_by = 'dummy-seed'
    OR (created_by = 'hsgmaster' AND change_reason LIKE '%test%');

ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard;

DELETE FROM market_price_raw WHERE collected_at < '2026-10-29';

-- ── 3. 확인 ─────────────────────────────────────────────────────────
SELECT '남은 시세 ' || created_by || ' ' || count(*) || '건'
  FROM market_prices_effective GROUP BY created_by;
SELECT '남은 수집원본 ' || count(*) || '건' FROM market_price_raw;
SELECT '남은 상품 ' || count(*) || '개' FROM products;
SELECT '주문 ' || count(*) || '건 (그대로)' FROM orders;

COMMIT;
