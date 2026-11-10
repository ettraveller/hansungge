-- 배너의 휴대폰용 이미지.
--
-- 히어로의 비율이 화면 폭에 따라 뒤집힌다 — 데스크톱은 1280×560(가로로 긴 2.3:1),
-- 휴대폰은 390×460(세로로 긴 0.85:1). 한 장으로 둘 다 덮으면 cover 가 가로를
-- 3분의 2쯤 잘라내므로, 이미지 안에 넣은 문구가 휴대폰에서 화면 밖으로 나간다.
--
-- NULL 이면 가로용 한 장을 그대로 쓴다. 휴대폰용을 반드시 올려야 한다면 배너를
-- 바꾸는 일이 두 배로 번거로워지고, 급할 때 한 장만 올리는 길이 막힌다.
ALTER TABLE hero_banners ADD COLUMN mobile_image_key VARCHAR(300);

COMMENT ON COLUMN hero_banners.mobile_image_key IS '휴대폰용 이미지. 비우면 image_key 를 쓴다';
