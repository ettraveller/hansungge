-- V48: 상단 유틸 띠의 링크 (2026-11-19)
--
-- 헤더 위 가는 띠에 블로그·유튜브·인스타그램 같은 아이콘 버튼과 「스마트스토어」
-- 「농협몰」 같은 글자 링크를 건다. 무엇을 걸지는 가게가 정하는 것이라 — 입점한
-- 몰이 늘 때마다 배포하게 둘 수 없다. 관리자 「메인 배너 & 푸터」에서 고친다.
--
-- 그동안은 링크가 「스마트스토어 주소」 하나였고 system_settings 의 한 칸
-- (FOOTER_STORE_URL)에 살았다. 칸 하나로는 둘째 링크를 받을 수 없어 표로 옮긴다.
CREATE TABLE top_links (
    id          BIGSERIAL PRIMARY KEY,
    -- 글자 링크면 화면에 보이는 이름이고, 아이콘 링크면 화면 낭독기가 읽는 이름이다.
    -- 그래서 아이콘이 있어도 비울 수 없다 — 이름 없는 그림 버튼은 눈으로 보지 않는
    -- 사람에게 "링크"라고만 읽힌다.
    label       VARCHAR(40)  NOT NULL,
    href        VARCHAR(500) NOT NULL,
    -- 올린 아이콘의 미디어 키. NULL 이면 글자 링크다.
    icon_key    VARCHAR(300),
    sort        INTEGER      NOT NULL DEFAULT 0,
    created_by  VARCHAR(60),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE top_links IS '상단 유틸 띠의 링크. 관리자 화면에서 통째로 갈아 끼운다 (전체 교체)';
COMMENT ON COLUMN top_links.icon_key IS '아이콘 미디어 키. NULL 이면 label 을 글자 링크로 그린다';
COMMENT ON COLUMN top_links.sort IS '작을수록 먼저 나온다. 관리자 화면이 0부터 다시 매긴다';

-- 적어 둔 스마트스토어 주소가 있으면 첫 링크로 옮긴다. 옮기지 않으면 이 배포와
-- 함께 띠에서 링크가 말없이 사라진다.
INSERT INTO top_links (label, href, sort, created_by)
SELECT '스마트스토어', btrim(value), 0, 'V48'
FROM system_settings
WHERE key = 'FOOTER_STORE_URL'
  AND btrim(value) ~* '^https?://';

-- 옮긴 칸은 지운다. 남겨 두면 아무도 읽지 않는 값이 "설정된 것처럼" 표에 남는다.
DELETE FROM system_settings WHERE key = 'FOOTER_STORE_URL';
