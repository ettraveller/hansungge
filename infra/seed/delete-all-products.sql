-- =====================================================================
-- 상품 전체 삭제 (오픈 전 정리용)
--
-- ⚠️ 관리자 화면에는 상품 삭제가 없다. 일부러 없다 — 상품을 지우면 그 상품을
--    담은 과거 주문의 상품 정보가 사라지기 때문이고, 그래서 판매 종료는
--    DISCONTINUED 상태로 표현한다 (AdminProductController).
--
--    이 파일은 그 규칙의 예외다. <b>오픈 전에 테스트로 만든 상품을 걷어내는
--    일회성 작업</b>에만 쓴다. 고객이 주문한 적 있는 상품에는 쓰지 않는다.
--
-- 지우는 것
--   · products 전부와 그 자식(옵션·이미지·가격구성요소·문의)
--   · cart_items 전부 — 지울 상품을 담은 줄만 골라 지우면 "담을 때의 시세"가
--     사라진 다른 줄이 남아 장바구니 조회가 깨진다
--
-- 남기는 것
--   · 시세 (market_prices_effective) — 상품과 무관하다. 지우면 다시 고시해야 한다
--   · 주문 (orders · order_items) — order_items 는 주문 당시의 상품을 복사해
--     두므로 상품을 지워도 남는다. 거래 기록이라 지우지 않는다
--     (전자상거래법 §6 — 5년 보존)
--   · 위시리스트·최근본상품 — FK 가 CASCADE 라 따라 지워진다
--
-- ---------------------------------------------------------------------
-- 실행
--
--   ⚠️ 파일을 /tmp 에 둬야 한다. postgres 계정은 /home/hanseongge 를 읽을 수
--      없다 (drwxr-x---) — 홈에 두면 "Permission denied" 가 난다.
--
--   개발 PC:  scp infra/seed/delete-all-products.sql \
--               hanseongge@49.50.129.220:/tmp/
--
--   서버(root):  su - postgres -c \
--                  'psql -d hanseongge -v confirm=YES -f /tmp/delete-all-products.sql'
--
-- ⚠️ -v confirm=YES 가 없으면 아무것도 지우지 않고 목록만 보여준다.
--    실수로 실행하는 일을 막기 위해서다. 먼저 그대로 돌려 목록을 확인할 것.
--
-- ⚠️ 먼저 백업할 것:
--   su - postgres -c 'pg_dump -d hanseongge \
--     -t products -t product_options -t product_images \
--     -t product_price_components -t cart_items -t qnas' > /tmp/before-products.sql
-- =====================================================================

\set ON_ERROR_STOP on

\if :{?confirm}
\else
  \set confirm NO
\endif

\echo ''
\echo '현재 상품 목록'
SELECT id, name, status, active, created_at
  FROM products
 ORDER BY id;

\if :confirm
\else
  \echo ''
  \echo '>>> 아무것도 지우지 않았습니다.'
  \echo '>>> 위 목록이 맞다면 -v confirm=YES 를 붙여 다시 실행하세요.'
  \q
\endif

BEGIN;

-- 순서가 중요하다. 상품의 자식 FK 는 대부분 ON DELETE 제한이라 손으로 지운다.
-- 장바구니가 가장 먼저다 — 상품뿐 아니라 담을 때의 시세도 참조한다
-- (cart_items.effective_price_id_at_add).
DELETE FROM cart_items;
DELETE FROM product_price_components;
DELETE FROM product_images;
DELETE FROM product_options;
DELETE FROM qnas;
DELETE FROM products;

\echo ''
\echo '정리 후'
SELECT count(*) AS 남은_상품 FROM products;
SELECT count(*) AS 남은_옵션 FROM product_options;
SELECT count(*) AS 남은_이미지 FROM product_images;
SELECT count(*) AS 주문_그대로 FROM orders;
SELECT created_by, count(*) AS 시세_그대로
  FROM market_prices_effective GROUP BY created_by ORDER BY created_by;

COMMIT;

-- 다음에 등록할 상품이 1번부터 시작하게 한다. 번호가 이어지면 "3번 상품"이
-- 예전 테스트 상품을 가리키던 번호와 겹쳐 나중에 로그를 읽을 때 헷갈린다.
--
-- ⚠️ COMMIT <b>뒤</b>에 둔다. 시퀀스 변경은 트랜잭션에 묶이지 않아서, BEGIN 안에
-- 두면 롤백돼도 시퀀스만 1 로 남는다. 그 상태로 상품을 등록하면 이미 있는 id 와
-- 부딪혀 저장이 실패한다 — 2026-10-30 리허설에서 실제로 그렇게 됐다.
ALTER SEQUENCE products_id_seq RESTART WITH 1;
ALTER SEQUENCE product_options_id_seq RESTART WITH 1;
ALTER SEQUENCE product_images_id_seq RESTART WITH 1;
