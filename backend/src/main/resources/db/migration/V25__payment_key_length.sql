-- =====================================================================
-- V25__payment_key_length.sql : PG 거래번호 자리를 넓힌다 (STEP 13·14 착수)
--
-- V4 를 쓸 때는 PG사가 정해지지 않아 pg_tid 를 VARCHAR(100) 으로 잡아 두었다.
-- 2026-09-28 토스페이먼츠로 정하면서 실제 규격을 확인했다 — paymentKey 는 최대 200자다.
--
-- 100 인 채로 두면 평소에는 멀쩡하다가(대개 60~70자) 긴 키가 오는 날 승인 저장이
-- 터진다. 그때는 **고객 돈은 이미 빠져나간 뒤**이고 우리 DB 에만 기록이 없다 —
-- 가장 고치기 어려운 종류의 사고다. 값이 들어오기 전에 넓혀 둔다.
--
-- idempotency_key 는 "provider + tx_type + pg_tid" 라 pg_tid 보다 길다. 같이 넓힌다.
-- =====================================================================

ALTER TABLE payments            ALTER COLUMN pg_tid          TYPE VARCHAR(200);
ALTER TABLE payment_transactions ALTER COLUMN idempotency_key TYPE VARCHAR(240);

COMMENT ON COLUMN payments.pg_tid IS
    '토스페이먼츠 paymentKey (최대 200자). 승인 후에만 채워진다';
