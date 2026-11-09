-- 진열 순서로 상품 목록을 정렬한다.
--
-- products.sort 는 V1 부터 있었지만 상품 목록 정렬에는 쓰이지 않았다 (id DESC 고정).
-- 관리자가 진열 순서를 직접 정하게 되면서 정렬 키가 (sort, id) 로 바뀐다.
--
-- 인덱스가 없으면 그 정렬이 전건 스캔 뒤 정렬로 떨어진다. 지금은 상품이 적어
-- 차이가 없지만, 목록은 쇼핑몰에서 가장 자주 열리는 화면이라 미리 둔다.
--
-- 값은 건드리지 않는다. 전부 0 이라 지금까지와 같은 순서(최신 등록순)가 그대로
-- 유지되고, 관리자가 순서를 저장하는 순간부터 그 순서로 바뀐다.
CREATE INDEX IF NOT EXISTS idx_prod_display_order ON products (sort, id DESC);

COMMENT ON COLUMN products.sort IS
    '진열 순서. 작을수록 앞. 같은 값이면 id DESC(최신순). 관리자 진열 화면이 0부터 다시 매긴다.';
