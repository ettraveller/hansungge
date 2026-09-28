-- =====================================================================
-- V26__wishlist_recently_viewed.sql : 찜한 상품 / 최근 본 상품
--
-- 2026-09-28 마이페이지 개편. 둘 다 "회원의 관심"을 담는 가벼운 표다.
--
-- 왜 상품에 FK 를 거나 — 주문(order_items)과 반대다.
--   주문은 산 사실이라 상품이 지워져도 남아야 한다. 이 둘은 <b>지금 관심 있는 것</b>이라
--   상품이 사라지면 같이 사라지는 게 맞다. 없어진 상품을 찜 목록에 남겨 두면
--   고객이 눌렀을 때 404 만 만난다.
-- =====================================================================

-- ---------- 찜한 상품 ----------
CREATE TABLE wishlist_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 같은 상품을 두 번 찜할 수 없다. 화면에서 두 번 눌러도 한 줄이다.
    CONSTRAINT ux_wishlist UNIQUE (user_id, product_id)
);
CREATE INDEX idx_wishlist_user ON wishlist_items (user_id, created_at DESC);

-- ---------- 최근 본 상품 ----------
--
-- 회원당 한 상품에 한 줄이고, 다시 보면 viewed_at 만 올린다(UPSERT).
-- 볼 때마다 줄을 쌓으면 같은 상품이 목록을 도배하고, 표가 끝없이 자란다.
CREATE TABLE recently_viewed (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    viewed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_recently_viewed UNIQUE (user_id, product_id)
);
CREATE INDEX idx_recently_viewed_user ON recently_viewed (user_id, viewed_at DESC);

COMMENT ON TABLE wishlist_items IS '찜한 상품. 상품이 지워지면 함께 지워진다';
COMMENT ON TABLE recently_viewed IS '최근 본 상품. 회원당 상품 하나에 한 줄, 다시 보면 viewed_at 만 갱신';
