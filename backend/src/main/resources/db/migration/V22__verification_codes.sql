-- =====================================================================
-- V22__verification_codes.sql : 이메일 인증번호 → 메일·문자 공용 인증번호
--
-- 인증번호 로그인(SMS)이 생기면서 "6자리 코드를 만들어 해시로 두고, 5분·5회·60초로
-- 묶는" 규칙이 두 채널에 필요해졌다. 테이블을 둘로 나누면 그 규칙도 두 벌이 되고,
-- 한쪽만 고치는 사고가 난다. 그래서 하나로 합치고 채널을 컬럼으로 둔다.
--
--   channel = EMAIL  target = 소문자 이메일        purpose = EMAIL_VERIFY
--   channel = SMS    target = 숫자만 남긴 휴대폰   purpose = LOGIN
--
-- 비밀번호 찾기는 더 이상 인증번호를 쓰지 않는다 — 휴대폰 본인확인으로 바뀌었다
-- (V21 참조: 로그인 ID 가 휴대폰이고, 이메일은 없을 수도 있다).
-- =====================================================================

-- 인증번호는 수명이 5분이다. 옮길 가치가 있는 행이 없고, 이전 purpose 값
-- (SIGNUP·PASSWORD_RESET)은 새 CHECK 에 들어가지 않는다.
TRUNCATE TABLE email_verifications;

ALTER TABLE email_verifications RENAME TO verification_codes;
ALTER TABLE verification_codes RENAME COLUMN email TO target;

ALTER TABLE verification_codes
    ADD COLUMN channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL', 'SMS'));

ALTER TABLE verification_codes DROP CONSTRAINT IF EXISTS email_verifications_purpose_check;
ALTER TABLE verification_codes
    ADD CONSTRAINT chk_vc_purpose CHECK (purpose IN ('LOGIN', 'EMAIL_VERIFY'));

-- 옛 인덱스는 lower(email) 기준이었다. 대상은 저장 전에 이미 정규화(소문자·숫자만)되므로
-- 함수 없이 건다 — 조회도 정규화한 값으로 한다.
DROP INDEX IF EXISTS idx_ev_lookup;
CREATE INDEX idx_vc_lookup ON verification_codes (target, channel, purpose, created_at DESC);

COMMENT ON TABLE  verification_codes         IS '인증번호 (메일·문자 공용). 원문을 저장하지 않는다';
COMMENT ON COLUMN verification_codes.target  IS '보낸 곳. 이메일은 소문자, 휴대폰은 숫자만';
COMMENT ON COLUMN verification_codes.channel IS 'EMAIL | SMS';
COMMENT ON COLUMN verification_codes.code_hash IS 'SHA-256. 원문을 저장하면 DB 를 읽을 수 있는 사람이 남의 로그인을 가로챌 수 있다';
