-- =====================================================================
-- V12__board.sql : 게시판 (공지 · 이벤트 · 문의 · 제품문의)
--
-- V5 가 notices / events / qnas 를 이미 만들어 두었다. 그때는 테이블만 세워 둔
-- 상태였고 Java 코드가 붙은 적이 없다. 실제로 게시판을 굴리려니 몇 가지가 빈다.
--
-- 테이블을 새로 만들지 않고 <b>V5 를 살려서 부족한 것만 더한다.</b> 같은 뜻의
-- 테이블을 두 벌 두면 어느 쪽이 진짜인지 나중에 아무도 모른다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. qnas — 문의게시판과 제품문의를 가른다
--
-- 두 게시판은 화면이 다르고 답변하는 사람도 다르지만 구조가 같아서 한 테이블을
-- 쓴다. 문제는 "이 글이 어느 게시판 것인가"를 무엇으로 판단하느냐다.
--
-- product_id 의 NULL 여부로 가르지 않는다. 제품문의인데 상품을 특정하지 못한 글
-- ("골드바 한 돈 얼마인가요")이 조용히 문의게시판으로 새어 들어간다 — 레퍼런스의
-- 제품문의 게시판에도 그런 글이 실제로 있다. 소속은 소속대로 적는다.
-- ---------------------------------------------------------------------
ALTER TABLE qnas
    ADD COLUMN board_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN view_count INT         NOT NULL DEFAULT 0,
    ADD COLUMN active     BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE qnas
    ADD CONSTRAINT chk_qna_board_type CHECK (board_type IN ('GENERAL', 'PRODUCT'));

-- 기본값은 이 마이그레이션에서만 필요하다(기존 행 채우기). 앞으로 들어오는 글은
-- 애플리케이션이 반드시 board_type 을 정해서 넣어야 하므로 기본값을 걷어낸다 —
-- 빠뜨린 코드가 조용히 GENERAL 로 저장되면 제품문의 글이 사라진 것처럼 보인다.
ALTER TABLE qnas ALTER COLUMN board_type DROP DEFAULT;

-- 게시판별 목록 조회. 정렬이 id DESC 로 고정이라 인덱스에 함께 넣는다.
CREATE INDEX idx_qna_board ON qnas(board_type, active, id DESC);

COMMENT ON COLUMN qnas.board_type IS '소속 게시판. GENERAL=문의게시판, PRODUCT=제품문의';
COMMENT ON COLUMN qnas.active     IS 'FALSE 면 삭제된 글. 행을 지우지 않는 이유는 답변 이력이 함께 사라지기 때문이다';

-- ---------------------------------------------------------------------
-- 2. events — 조회수와 수정시각
-- ---------------------------------------------------------------------
ALTER TABLE events
    ADD COLUMN view_count INT         NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 공개 목록은 진행 중인 이벤트를 먼저 보여준다.
CREATE INDEX idx_event_active ON events(active, start_at DESC);

-- ---------------------------------------------------------------------
-- 3. event_entries — 이벤트 응모글 (신규)
--
-- 이벤트는 관리자가 열고, 그 아래에 고객이 응모글을 단다. events 에 컬럼을
-- 더해서 될 일이 아니라 별도 테이블이다 — 이벤트 하나에 응모가 여러 건이다.
--
-- 응모글에 제목이 없는 것은 의도다. 레퍼런스의 응모글은 전부 "기프티콘
-- 신청합니다" 류라 제목과 본문이 같은 말이 된다. 한 줄만 받는다.
-- ---------------------------------------------------------------------
CREATE TABLE event_entries (
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events(id),
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    content    TEXT        NOT NULL,
    -- 응모글에는 연락처가 섞이기 쉽다. 기본을 비밀글로 두어, 실수로 공개되는
    -- 쪽이 아니라 일부러 공개하는 쪽이 되게 한다.
    secret     BOOLEAN     NOT NULL DEFAULT TRUE,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_entry_event ON event_entries(event_id, active, id DESC);
CREATE INDEX idx_event_entry_user  ON event_entries(user_id, id DESC);

COMMENT ON TABLE event_entries IS '이벤트 응모글. 관리자가 연 이벤트(events)에 고객이 단다';

-- ---------------------------------------------------------------------
-- notices 는 손대지 않는다 — pinned·view_count·active·updated_at 이 V5 에 이미 있다.
-- news·faqs·stores 도 그대로 둔다. 이번 범위가 아니다
-- (뉴스센터는 만들지 않기로 했고, FAQ·지점안내는 별도 화면이 있다).
-- ---------------------------------------------------------------------
