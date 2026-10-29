-- =====================================================================
-- V31__product_image_kind.sql : 상품 이미지를 두 갈래로 나눈다
--
-- 2026-10-29. 지금 product_images 는 한 묶음이고, 상품 상세가 그것을 어떻게
-- 보여줄지는 화면이 임의로 정했다. 그런데 실제로 올라오는 것은 성격이 전혀
-- 다른 두 종류다.
--
--   GALLERY  상품 사진. 정사각형에 가깝고, 상세 상단에서 썸네일로 넘겨 본다.
--   DETAIL   상세페이지. 860×9531 처럼 세로로 길고, 설명 아래에 전체 폭으로
--            쌓아 보여준다.
--
-- 섞어 두면 어느 쪽으로 그려도 한쪽이 깨진다 — 긴 상세페이지를 정사각형에
-- 넣으면 맨 위 한 조각만 보이고, 상품 사진을 전체 폭으로 늘리면 흐려진다.
--
-- ---------------------------------------------------------------------
-- products.thumbnail_key 는 남긴다
--
-- 목록·검색결과·장바구니·주문서는 <b>정확히 한 장</b>을 쓰는 자리다. 매번
-- product_images 를 조인해 첫 장을 고르게 하는 대신, 대표 한 장을 그 컬럼에
-- 둔 채로 쓴다. order_items 는 썸네일을 스냅샷하지 않으므로(주문 당시 사진을
-- 보관하지 않는다) 그 컬럼이 비면 과거 주문 화면의 사진 자리도 함께 빈다.
--
-- ⚠️ 이제 그 컬럼은 <b>GALLERY 첫 장의 사본</b>이다. 관리자가 따로 입력하지
-- 않고, ProductService 가 갤러리를 교체할 때 함께 맞춘다. 손으로 UPDATE 하면
-- 다음 교체에서 덮어써진다.
-- =====================================================================

ALTER TABLE product_images
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'DETAIL';

-- 기존 행은 DETAIL 로 둔다. 이 기능이 붙기 전에 넣어 둔 키는 상세 설명에
-- 쓰려던 것이고, 갤러리로 올리면 비율이 맞지 않아 잘려 보인다.
-- (2026-10-29 운영 기준 이 표는 비어 있다. 로컬·개발 데이터를 위한 것이다.)

ALTER TABLE product_images
    ADD CONSTRAINT chk_pi_kind CHECK (kind IN ('GALLERY', 'DETAIL'));

-- DEFAULT 를 떼어낸다. 어느 갈래인지는 넣는 코드가 정해야 한다 —
-- 기본값이 남아 있으면 kind 를 빠뜨린 코드가 조용히 DETAIL 로 들어간다.
ALTER TABLE product_images
    ALTER COLUMN kind DROP DEFAULT;

-- 조회는 언제나 "이 상품의 이 갈래를 순서대로"다.
DROP INDEX IF EXISTS idx_img_product;
CREATE INDEX idx_product_images_lookup
    ON product_images (product_id, kind, sort, id);

-- 이미 thumbnail_key 가 있는 상품은 그 한 장을 갤러리 첫 장으로 옮겨 둔다.
-- 그러지 않으면 관리자 화면의 대표이미지 칸이 비어 보이고, 저장하는 순간
-- thumbnail_key 까지 지워진다.
INSERT INTO product_images (product_id, object_key, sort, kind)
SELECT p.id, p.thumbnail_key, 0, 'GALLERY'
  FROM products p
 WHERE p.thumbnail_key IS NOT NULL
   AND p.thumbnail_key <> ''
   AND NOT EXISTS (
       SELECT 1 FROM product_images pi
        WHERE pi.product_id = p.id AND pi.kind = 'GALLERY');

COMMENT ON COLUMN product_images.kind IS
    'GALLERY = 상세 상단 상품 사진, DETAIL = 설명 아래 상세페이지 이미지';
