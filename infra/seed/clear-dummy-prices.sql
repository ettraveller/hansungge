-- =====================================================================
-- 개발용 더미 시세 지우기
--
-- market_prices_effective 는 append-only 다. DELETE 를 트리거가 막는다:
--   ERROR: market_prices_effective is append-only (DELETE forbidden)
--
-- 그 트리거는 운영에서 지켜야 하는 규칙이고, 여기서 잠깐 내리는 것은
-- <b>개발 DB 에서 더미를 걷어낼 때만</b> 하는 일이다. 운영에서 실행하지 말 것.
--
-- 실행:
--   psql -h localhost -U hanseongge -d hanseongge -f clear-dummy-prices.sql
-- =====================================================================

BEGIN;

-- 안전장치: 더미가 아닌 행이 섞여 있으면 멈춘다.
-- 사람이 손으로 고시한 값을 이 스크립트가 쓸어가는 사고를 막는다.
DO $$
DECLARE
    real_rows  bigint;
    dummy_rows bigint;
BEGIN
    SELECT count(*) FILTER (WHERE created_by <> 'dummy-seed'),
           count(*) FILTER (WHERE created_by =  'dummy-seed')
      INTO real_rows, dummy_rows
      FROM market_prices_effective;

    RAISE NOTICE '더미 % 행 / 더미가 아닌 행 %', dummy_rows, real_rows;

    IF real_rows > 0 THEN
        RAISE EXCEPTION
            '더미가 아닌 시세가 % 건 있습니다. 사람이 고시한 값일 수 있어 멈춥니다. '
            '정말 지우려면 이 블록을 지우고 다시 실행하세요.', real_rows;
    END IF;
END $$;

ALTER TABLE market_prices_effective DISABLE TRIGGER trg_mpe_guard;

DELETE FROM market_prices_effective WHERE created_by = 'dummy-seed';

ALTER TABLE market_prices_effective ENABLE TRIGGER trg_mpe_guard;

COMMIT;

SELECT count(*) AS "남은 시세 행" FROM market_prices_effective;
