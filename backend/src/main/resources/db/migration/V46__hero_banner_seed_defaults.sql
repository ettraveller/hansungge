-- 코드에 박혀 있던 기본 배너 세 장을 DB 로 옮긴다.
--
-- home-content.ts 의 HERO_SLIDES 가 그것이다. 그 파일 주석에도 "배너 관리가 생기면
-- 이 파일을 지운다" 고 적혀 있었다 — 이제 생겼다. 옮기고 나면 관리자 화면에 그대로
-- 보이고, 글자 한 줄을 고치는 데 배포가 필요 없어진다.
--
-- ⚠️ 배너가 이미 있으면 아무것도 하지 않는다. 운영이 올려 둔 배너 뒤에 기본 배너
-- 세 장이 따라 붙으면, 내린 줄 알았던 문구가 손님 화면에 다시 걸린다.
--
-- 배경 사진은 담지 않는다. 첫 장이 쓰던 /hero.jpg 는 화면 코드에 딸린 파일이지
-- 업로드된 이미지가 아니라서, 배너의 image_key 로 가리킬 수 없다 (그 칸은 업로드
-- 키만 받는다 — 앞에 / 가 붙은 값을 받기 시작하면 바깥 주소가 함께 열린다).
-- 사진이 필요하면 관리자 화면에서 한 번 올리면 된다.
INSERT INTO hero_banners (kind, content, alt_text, sort, active, created_by, updated_by)
SELECT * FROM (VALUES
    ('DESIGN', '{
        "background": {"color": "#0f1b2d", "blur": 0, "overlay": "GRADIENT"},
        "align": "LEFT",
        "blocks": [
            {"id": "b1", "type": "TEXT", "style": "EYEBROW", "text": "HANSEONG GOLD EXCHANGE"},
            {"id": "b2", "type": "TEXT", "style": "TITLE", "text": "오늘의 정확한 시세로"},
            {"id": "b3", "type": "TEXT", "style": "HIGHLIGHT", "text": "안심하고 거래하세요"},
            {"id": "b4", "type": "TEXT", "style": "DESC", "text": "당사가 고시한 시세를 그대로 적용합니다. 최종 금액은 주문하시는 시각의 고시 시세로 확정됩니다."},
            {"id": "b5", "type": "BUTTON", "text": "골드바 보러가기", "href": "/shop?categoryId=1", "look": "GOLD"}
        ]
    }'::jsonb, '오늘의 정확한 시세로 안심하고 거래하세요', 0, TRUE, 'migration', 'migration'),

    ('DESIGN', '{
        "background": {"color": "#0f1b2d", "blur": 0, "overlay": "GRADIENT"},
        "align": "LEFT",
        "blocks": [
            {"id": "b1", "type": "TEXT", "style": "EYEBROW", "text": "PURE GOLD 999.9"},
            {"id": "b2", "type": "TEXT", "style": "TITLE", "text": "순도 99.99% 골드바"},
            {"id": "b3", "type": "TEXT", "style": "HIGHLIGHT", "text": "보증서와 함께"},
            {"id": "b4", "type": "TEXT", "style": "DESC", "text": "1돈부터 10돈까지. 모든 제품에 품질보증서를 발급해 재매입 시에도 가치를 인정받습니다."},
            {"id": "b5", "type": "BUTTON", "text": "골드바 보러가기", "href": "/shop?categoryId=1", "look": "GOLD"}
        ]
    }'::jsonb, '순도 99.99% 골드바, 보증서와 함께', 1, TRUE, 'migration', 'migration'),

    ('DESIGN', '{
        "background": {"color": "#0f1b2d", "blur": 0, "overlay": "GRADIENT"},
        "align": "LEFT",
        "blocks": [
            {"id": "b1", "type": "TEXT", "style": "EYEBROW", "text": "BUY-BACK SERVICE"},
            {"id": "b2", "type": "TEXT", "style": "TITLE", "text": "보유하신 귀금속"},
            {"id": "b3", "type": "TEXT", "style": "HIGHLIGHT", "text": "최고가 매입"},
            {"id": "b4", "type": "TEXT", "style": "DESC", "text": "돌반지·목걸이 등 보유하신 귀금속을 당일 고시 시세로 매입해 드립니다."},
            {"id": "b5", "type": "BUTTON", "text": "매입 절차 안내", "href": "/purchase-guide", "look": "GOLD"}
        ]
    }'::jsonb, '보유하신 귀금속 최고가 매입', 2, TRUE, 'migration', 'migration')
) AS seed(kind, content, alt_text, sort, active, created_by, updated_by)
WHERE NOT EXISTS (SELECT 1 FROM hero_banners);
