-- =====================================================================
-- V7 — 시세 수집 로그 (STEP 6, 개발계획 §12.2 L1-b / 결함 D-3)
--
-- V2 의 market_price_raw 는 수집 실패를 기록할 수 없다:
--   price_per_gram > 0 CHECK  ↔  status='FAILED'
-- 실패에는 가격이 없는데 컬럼은 양수를 요구한다. 그래서 "API 오류/타임아웃/
-- 파싱실패/범위이탈"이 어디에도 남지 않고, 대시보드의 "마지막 성공 시각"도
-- 만들 수 없다. 실패를 원본 테이블이 아니라 별도 로그로 분리해 해소한다.
--
-- ※ 이 파일은 TODO C-1 의 12건 중 D-3 하나만 해소한다. 나머지 11건(D-1·D-2·
--    D-4·D-5·D-6~D-12)은 손대지 않았다. STEP 6 이 이 테이블 없이는 요구사항을
--    만족할 수 없어 먼저 떼어온 것뿐이다.
-- =====================================================================

CREATE TABLE market_price_fetch_logs (
    id          BIGSERIAL PRIMARY KEY,
    source_id   BIGINT      NOT NULL REFERENCES market_price_sources(id),
    metal_type  VARCHAR(20),
    result      VARCHAR(20) NOT NULL
                CHECK (result IN ('SUCCESS','SKIPPED_DUPLICATE','HTTP_ERROR',
                                  'TIMEOUT','PARSE_ERROR','INVALID_VALUE','EXCEPTION')),
    -- SUCCESS 일 때만 채워진다. 실패에는 원본 행이 만들어지지 않기 때문이다 —
    -- 이것이 "수집 실패가 시세를 0원으로 만들지 않는다"의 구조적 근거다.
    raw_id      BIGINT REFERENCES market_price_raw(id),
    http_status INT,
    elapsed_ms  INT,
    message     VARCHAR(500),
    attempt_no  INT         NOT NULL DEFAULT 1 CHECK (attempt_no >= 1),
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mpfl_src_time ON market_price_fetch_logs(source_id, started_at DESC);
CREATE INDEX idx_mpfl_result   ON market_price_fetch_logs(result, started_at DESC);

COMMENT ON TABLE  market_price_fetch_logs IS '시세 수집 시도 이력. 성공·실패 전부. 대시보드의 수집상태/마지막 성공시각 출처';
COMMENT ON COLUMN market_price_fetch_logs.raw_id IS 'SUCCESS 일 때 생성된 market_price_raw. 실패 시 NULL';

-- 이 로그도 사후 수정 대상이 아니다. 수집 이력을 고칠 수 있으면 장애 분석의 근거가 되지 못한다.
CREATE OR REPLACE FUNCTION fn_mpfl_guard() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'market_price_fetch_logs is append-only (% forbidden)', TG_OP;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mpfl_guard
    BEFORE UPDATE OR DELETE ON market_price_fetch_logs
    FOR EACH ROW EXECUTE FUNCTION fn_mpfl_guard();
