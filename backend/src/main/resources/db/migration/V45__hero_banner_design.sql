-- 배너를 화면에서 꾸민다.
--
-- 지금까지 배너는 사진 한 장이었다. 문구·버튼까지 이미지 안에 넣는 방식이라
-- 글자 한 줄을 고치려면 디자이너에게 사진을 다시 받아야 했다.
--
-- 이제 두 갈래를 둔다.
--   IMAGE   — 지금까지의 배너. 사진 한 장이 전부다.
--   DESIGN  — 배경(색·사진·흐림)과 그 위에 쌓는 글자·버튼을 관리자가 짠다.
--
-- 쌓기 방식인 이유 — 배너의 비율이 폭에 따라 뒤집힌다(PC 2.3:1, 휴대폰 0.85:1).
-- 요소를 좌표로 놓게 하면 PC 에서 오른쪽 끝에 둔 글자가 휴대폰에서는 화면 밖이라,
-- 배너 한 장마다 배치를 두 번 잡아야 한다. 위에서 아래로 쌓으면 지금 CSS 가
-- 두 폭을 그대로 처리한다.
ALTER TABLE hero_banners
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    ADD COLUMN content JSONB;

-- 사진 없는 배너가 생긴다. DESIGN 은 배경색만으로도 성립한다.
ALTER TABLE hero_banners ALTER COLUMN image_key DROP NOT NULL;

-- 둘 중 하나는 반드시 있어야 한다. 사진도 내용도 없는 배너는 메인 맨 위에
-- 검은 상자만 띄운다 — 화면에서 막지만, 그 한 겹이 뚫렸을 때 DB 가 받아 주면
-- 고객이 보는 화면에 빈 배너가 걸린다.
ALTER TABLE hero_banners ADD CONSTRAINT ck_hero_banner_body CHECK (
    (kind = 'IMAGE'  AND image_key IS NOT NULL) OR
    (kind = 'DESIGN' AND content   IS NOT NULL)
);

COMMENT ON COLUMN hero_banners.kind IS 'IMAGE=사진 한 장, DESIGN=화면에서 꾸민 배너';
COMMENT ON COLUMN hero_banners.content IS 'DESIGN 배너의 내용. 모양은 BannerContent 참고';
