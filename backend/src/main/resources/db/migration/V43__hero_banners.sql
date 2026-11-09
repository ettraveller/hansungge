-- 메인 배너 — 관리자가 이미지로 갈아 끼운다.
--
-- 지금까지는 화면 코드(home-content.ts)에 세 장이 박혀 있었다. 배너는 원래
-- 운영이 바꾸는 것이라, 바꿀 때마다 배포해야 하는 상태였다.
--
-- 글자를 따로 담지 않는다. 배너의 문구·버튼까지 이미지 한 장에 들어가는 방식으로
-- 간다 — 디자인이 이미지 안에서 끝나야 글자 위치가 사진과 어긋나지 않는다.
CREATE TABLE hero_banners (
    id          BIGSERIAL PRIMARY KEY,
    image_key   VARCHAR(300) NOT NULL,
    -- 누르면 갈 곳. 비우면 누를 수 없는 배너가 된다 (안내용).
    link_href   VARCHAR(500),
    -- 화면 낭독기가 읽을 설명. 이미지에 글자가 들어 있으므로 비워 두면
    -- 눈으로 보지 않는 사람에게는 배너가 통째로 사라진다.
    alt_text    VARCHAR(200),
    sort        INTEGER NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(60),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(60),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_hero_banner_display ON hero_banners (active, sort, id);

COMMENT ON TABLE hero_banners IS '메인 배너. 관리자 화면에서 통째로 갈아 끼운다 (전체 교체)';
COMMENT ON COLUMN hero_banners.sort IS '작을수록 먼저 나온다. 관리자 화면이 0부터 다시 매긴다';
