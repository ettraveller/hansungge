-- =====================================================================
-- V10 — 장바구니 보강 (STEP 10, 개발계획 §13.6)
--
-- V4 의 cart_items 는 주문 구조를 잡을 때 함께 만들어진 뼈대다. 실제로 담고 빼는
-- 기능을 붙이기 전에 두 가지를 바로잡는다.
--
-- 1) option_id 를 NOT NULL 로.
--    D-7 해소로 "옵션 없는 상품" 개념이 사라졌다 — 상품을 만들면 기본 옵션이 반드시
--    하나 생기고 재고는 그곳에만 있다. 그런데 option_id 가 nullable 이면
--    ux_cart_item UNIQUE(cart_id, product_id, option_id) 가 <b>중복을 못 막는다.</b>
--    PostgreSQL 에서 NULL 은 서로 다른 값으로 취급되어, 같은 상품을 옵션 없이 두 번
--    담으면 UNIQUE 를 통과해 두 줄이 생긴다.
--
-- 2) 담은 시점의 근거 시세를 남긴다 (§0.2 추가 개선 권고).
--    price_at_add 만으로는 "그때 얼마였다"는 알아도 "어느 고시 시세 기준이었는지"는
--    모른다. 장바구니 화면이 "담을 때보다 12,000원 올랐습니다"를 보여줄 때 그 근거를
--    짚을 수 있어야 하고, 주문으로 넘어갈 때 시세가 몇 번 바뀌었는지도 이 값으로 안다.
--
-- 적용 시점(2026-08-27) carts/cart_items 는 비어 있다. NOT NULL 승격을 백필 없이
-- 지금 걸 수 있고, 데이터가 쌓이면 불가능해진다.
-- =====================================================================

ALTER TABLE cart_items ALTER COLUMN option_id SET NOT NULL;

-- 담은 시점의 근거 시세. 고정가 상품이면 NULL 이다 (시세를 참조하지 않는다).
ALTER TABLE cart_items ADD COLUMN effective_price_id_at_add BIGINT
      REFERENCES market_prices_effective (id);

-- 수량 변경 시각. added_at 은 처음 담은 때로 두어 "언제부터 담겨 있었나"를 남긴다.
ALTER TABLE cart_items ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 장바구니 조회는 항상 cart_id 로 전체를 읽는다.
CREATE INDEX idx_cart_item_cart ON cart_items (cart_id, added_at);

COMMENT ON COLUMN cart_items.price_at_add IS
    '담은 시점 단가. 표시·변동 비교 전용이며 결제 금액이 아니다 — 주문 금액은 항상 서버가 다시 계산한다';
COMMENT ON COLUMN cart_items.effective_price_id_at_add IS
    '담은 시점 근거 시세(market_prices_effective.id). 고정가 상품은 NULL';
