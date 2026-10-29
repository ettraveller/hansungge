-- =====================================================================
-- 주문 전체 삭제 (오픈 전 정리용)
--
-- ⚠️⚠️ 이 파일은 설계 원칙을 거스른다. 쓰기 전에 읽을 것.
--
--   주문은 <b>거래 기록</b>이다. 전자상거래법 §6 은 계약·청약철회 기록 5년,
--   대금결제 기록 5년, 소비자 불만 기록 3년 보존을 요구한다. 그래서 이 시스템은
--   주문을 지우는 경로를 아예 두지 않았고(관리자 화면에 삭제가 없다),
--   order_items 와 order_item_price_components 에는 UPDATE·DELETE 를 막는
--   트리거까지 걸어 뒀다 (D-2).
--
--   이 스크립트는 그 트리거를 일부러 끈다. <b>오픈 전, 개발·테스트 과정에서
--   만들어진 주문을 걷어내는 일회성 작업</b>에만 쓴다.
--
--   ⛔ 실제 고객 주문이 하나라도 섞여 있으면 쓰지 않는다. 지운 뒤에는 되돌릴
--      방법이 없고, 그 고객과 금액을 다투게 되면 근거가 남지 않는다.
--
-- 지우는 것
--   · orders 전부와 그에 매달린 모든 기록
--     (order_items · 가격구성요소 · 조정내역 · 결제 · 결제거래 ·
--      배송 · 상태이력, 쿠폰 발급의 주문 연결)
--   · 주문이 붙잡고 있던 테스트 시세 (선택 — 아래 :prices 참고)
--
-- 남기는 것
--   · 회원 · 쿠폰 · 시세 · 상품 — 주문과 별개다
--   · 쿠폰 발급 행 자체는 남기고 order_id 만 NULL 로 푼다. 쿠폰을 받은 사실은
--     주문과 별개의 기록이고, 그 컬럼은 NULL 을 허용한다
--
-- ---------------------------------------------------------------------
-- 실행
--
--   ⚠️ 파일을 /tmp 에 둬야 한다. postgres 계정은 /home/hanseongge 를 읽을 수
--      없다 (drwxr-x---).
--
--   개발 PC:  scp infra/seed/delete-all-orders.sql \
--               hanseongge@49.50.129.220:/tmp/
--
--   서버(root):
--     # ① 목록만 본다 — 아무것도 지우지 않는다
--     su - postgres -c 'psql -d hanseongge -f /tmp/delete-all-orders.sql'
--
--     # ② 목록이 맞으면 실행
--     su - postgres -c \
--       'psql -d hanseongge -v confirm=YES -f /tmp/delete-all-orders.sql'
--
-- ⚠️ 먼저 백업할 것. 되돌릴 방법은 이것뿐이다:
--   su - postgres -c 'pg_dump -d hanseongge \
--     -t orders -t order_items -t order_item_price_components \
--     -t order_item_adjustments -t payments -t payment_transactions \
--     -t shipments -t order_status_histories' > /tmp/before-orders.sql
-- =====================================================================

\set ON_ERROR_STOP on

\if :{?confirm}
\else
  \set confirm NO
\endif

\echo ''
\echo '지울 주문'
SELECT o.id, o.order_no, o.status, o.total_amount, o.ordered_at,
       (SELECT count(*) FROM order_items i WHERE i.order_id = o.id) AS 품목수,
       (SELECT count(*) FROM payments p WHERE p.order_id = o.id)    AS 결제건수
  FROM orders o
 ORDER BY o.id;

\echo ''
\echo '함께 사라지는 기록'
SELECT 'order_items' AS 표, count(*) FROM order_items
UNION ALL SELECT 'order_item_price_components', count(*) FROM order_item_price_components
UNION ALL SELECT 'order_item_adjustments',      count(*) FROM order_item_adjustments
UNION ALL SELECT 'payments',                    count(*) FROM payments
UNION ALL SELECT 'payment_transactions',        count(*) FROM payment_transactions
UNION ALL SELECT 'shipments',                   count(*) FROM shipments
UNION ALL SELECT 'order_status_histories',      count(*) FROM order_status_histories;

\if :confirm
\else
  \echo ''
  \echo '>>> 아무것도 지우지 않았습니다.'
  \echo '>>> ⚠️ 위 주문 중 실제 고객 주문이 있으면 여기서 멈추세요.'
  \echo '>>> 전부 테스트 주문이 맞다면 -v confirm=YES 를 붙여 다시 실행하세요.'
  \q
\endif

BEGIN;

-- order_items 와 가격구성요소는 append-only 다. 트리거가 DELETE 를 막는다 (D-2).
-- 한 트랜잭션 안에서 끄고 켠다 — 그 사이에 다른 주문이 들어와도 보호가 비는
-- 시간이 없도록.
ALTER TABLE order_items                  DISABLE TRIGGER trg_order_item_immutable;
ALTER TABLE order_item_price_components  DISABLE TRIGGER trg_oipc_immutable;

-- 잎에서 뿌리 방향으로 지운다. FK 가 전부 제한(a)이라 순서를 지켜야 한다.
DELETE FROM payment_transactions;
DELETE FROM payments;
DELETE FROM shipments;
DELETE FROM order_status_histories;
DELETE FROM order_item_adjustments;
DELETE FROM order_item_price_components;
DELETE FROM order_items;

-- 쿠폰을 받은 사실은 주문과 별개의 기록이라 행을 지우지 않는다. 주문으로 가는
-- 연결만 끊는다 — 그 컬럼은 NULL 을 허용한다.
UPDATE coupon_issues SET order_id = NULL WHERE order_id IS NOT NULL;

DELETE FROM orders;

ALTER TABLE order_item_price_components  ENABLE TRIGGER trg_oipc_immutable;
ALTER TABLE order_items                  ENABLE TRIGGER trg_order_item_immutable;

-- ── 주문이 붙잡고 있던 테스트 시세 ─────────────────────────────────
--
-- cleanup-test-data.sql 이 지우려다 FK 에 막혔던 행들이다. 주문이 사라졌으므로
-- 이제 지울 수 있다. 지금 적용 중인 고시(effective_to IS NULL)는 건드리지 않는다 —
-- 그것까지 지우면 상품 가격이 잡히지 않아 주문이 막힌다.
--
-- -v prices=KEEP 를 넘기면 이 단계를 건너뛴다.
\if :{?prices}
\else
  \set prices CLEAN
\endif

ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard;

DELETE FROM market_prices_effective e
 WHERE :'prices' = 'CLEAN'
   AND e.effective_to IS NOT NULL
   AND (e.created_by = 'dummy-seed'
        OR (e.created_by = 'hsgmaster' AND e.change_reason LIKE '%test%'))
   AND NOT EXISTS (SELECT 1 FROM cart_items ci WHERE ci.effective_price_id_at_add = e.id);

ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard;

-- ── 확인 ────────────────────────────────────────────────────────────
\echo ''
\echo '정리 후'
SELECT count(*) AS 남은_주문 FROM orders;
SELECT count(*) AS 남은_품목 FROM order_items;
SELECT count(*) AS 남은_결제 FROM payments;
SELECT created_by, count(*) AS 남은_시세,
       count(*) FILTER (WHERE effective_to IS NULL) AS 적용중
  FROM market_prices_effective GROUP BY created_by ORDER BY created_by;
SELECT count(*) AS 회원_그대로 FROM users;

COMMIT;

-- 다음 주문이 1번부터 시작하게 한다. 번호가 이어지면 지워진 테스트 주문의
-- 번호와 겹쳐 나중에 로그를 읽을 때 헷갈린다.
--
-- ⚠️ COMMIT 뒤에 둔다. 시퀀스 변경은 트랜잭션에 묶이지 않아서, BEGIN 안에 두면
-- 롤백돼도 시퀀스만 1 로 남고 그 상태로는 이미 있는 id 와 부딪힌다.
ALTER SEQUENCE orders_id_seq RESTART WITH 1;
ALTER SEQUENCE order_items_id_seq RESTART WITH 1;
