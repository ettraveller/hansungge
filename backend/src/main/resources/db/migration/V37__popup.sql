-- =====================================================================
-- V37 — 메인 팝업
--
-- 첫 화면에 띄우는 알림창. 지금 필요한 것은 매입 계산기를 광고처럼 띄우는
-- 일이지만, 다음에 올 것이 이벤트·휴무 안내·신상품이라 <b>관리자가 직접 넣고
-- 끄는 틀</b>로 만든다. 계산기도 그 틀에 들어가는 한 종류일 뿐이다.
--
-- 종류를 둘로 나눈 이유
--   CALCULATOR  매입 계산기를 그대로 띄운다. 내용이 코드에 있다
--   CONTENT     관리자가 쓴 제목·본문·이미지. 누르면 링크로 간다
--
--   이미지 한 장만 올리는 흔한 방식을 쓰지 않는다. 이미지에 적힌 글자는
--   읽어 주는 도구가 읽지 못하고, 검색에도 안 걸리고, 좁은 화면에서 글자가
--   뭉개진다. 제목·본문을 따로 받고 이미지는 곁들이로 둔다.
--
-- 노출 기간을 반드시 둔다
--   끝나는 날짜 없는 팝업은 반드시 잊혀진다. 지난 설 인사가 여름까지 떠
--   있는 쇼핑몰이 그래서 생긴다. starts_at 은 비워 둘 수 있지만(지금부터),
--   ends_at 도 비워 두면 사람이 꺼 줄 때까지 계속 뜬다는 뜻이다.
-- =====================================================================

CREATE TABLE popups (
    id          BIGSERIAL    PRIMARY KEY,

    -- 관리자 목록에서 구분하는 이름. 고객에게는 보이지 않는다
    name        VARCHAR(100) NOT NULL,

    kind        VARCHAR(20)  NOT NULL
                CHECK (kind IN ('CALCULATOR', 'CONTENT')),

    -- CONTENT 일 때 쓴다. CALCULATOR 는 비워 둔다
    title       VARCHAR(200),
    body        TEXT,
    image_key   VARCHAR(300),
    link_href   VARCHAR(500),
    link_label  VARCHAR(60),

    -- 노출 기간. 둘 다 비면 "켜 두는 동안 계속"
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,

    -- 끄고 켜기. 기간을 지우지 않고 잠시 내릴 때 쓴다
    active      BOOLEAN      NOT NULL DEFAULT TRUE,

    -- 여러 개가 동시에 뜰 때의 차례. 작을수록 먼저
    sort        INT          NOT NULL DEFAULT 0,

    /*
     * 「오늘 하루 보지 않기」를 띄울지.
     *
     * 끌 수 있어야 한다 — 휴무 안내처럼 반드시 봐야 하는 것은 하루 숨기면
     * 그날 방문한 사람이 끝내 못 본다. 기본은 켬이다. 매일 같은 팝업이
     * 뜨는 것만큼 빨리 미움받는 것이 없다.
     */
    dismissible BOOLEAN      NOT NULL DEFAULT TRUE,

    created_by  VARCHAR(60),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(60),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 기간을 거꾸로 넣는 사고를 DB 에서 막는다. 거꾸로면 영영 안 뜨는데,
    -- 화면에는 아무 일도 일어나지 않아 "왜 안 뜨지"로 한참을 헤맨다.
    CONSTRAINT ck_popups_period CHECK (ends_at IS NULL OR starts_at IS NULL OR starts_at < ends_at),

    -- CONTENT 는 제목이 있어야 한다. 제목 없는 알림창은 띄울 것이 없다
    CONSTRAINT ck_popups_content CHECK (kind <> 'CONTENT' OR title IS NOT NULL),

    -- 링크 글자만 있고 주소가 없으면 눌리지 않는 버튼이 생긴다
    CONSTRAINT ck_popups_link CHECK (link_label IS NULL OR link_href IS NOT NULL)
);

-- 고객 화면이 "지금 띄울 것"을 묻는 경로. 켜진 것만 보므로 부분 인덱스다
CREATE INDEX ix_popups_live ON popups (sort, id) WHERE active;

COMMENT ON COLUMN popups.name        IS '관리자용 구분 이름. 고객에게 보이지 않는다';
COMMENT ON COLUMN popups.kind        IS 'CALCULATOR(매입 계산기) / CONTENT(관리자가 쓴 내용)';
COMMENT ON COLUMN popups.dismissible IS '「오늘 하루 보지 않기」 제공 여부';

-- 첫 팝업 — 매입 계산기. 끄고 싶으면 관리자 화면에서 끈다
INSERT INTO popups (name, kind, link_href, link_label, dismissible, sort, created_by)
VALUES ('매입 계산기 안내', 'CALCULATOR', '/purchase-guide', '매입절차 자세히 보기', TRUE, 0, 'system');
