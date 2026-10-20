-- =====================================================================
-- V30__duplicate_key.sql : DI(중복가입확인정보) 판정 키
--
-- 2026-10-21. 지금 동일인 판정은 CI 해시(identity_key) 하나로 한다. 그 값은
-- 범용 연계 식별자라 이용기관의 의무가 무겁다 — 2024-08-14 시행 정보통신망법
-- §23조의5·6 은 목적 외 이용에 5년 이하 징역을 둔다.
--
-- DI 는 <b>이용기관별로 고유</b>해서 다른 곳의 값과 맞춰 볼 수 없다. 동일인 판정이라는
-- 목적에는 그것으로 충분하고, 새어 나가도 다른 서비스로 번지지 않는다. 그래서 장기적으로
-- 판정 키를 DI 쪽으로 옮기려 하고, 이 컬럼이 그 전환 경로다.
--
-- 지금 당장은 비어 있다. 간편인증(reqSvcCd=01)이 DI 를 주지 않기 때문이다 — 본인확인(03)
-- 이나 KG파이낸셜 휴대폰인증(TODO E-8)이 붙어야 값이 들어오기 시작한다.
--
-- CI 와 같은 방식으로 해시해 둔다. 원본을 두지 않는 이유는 같다 — 비교만 하면 되고,
-- 원본이 있으면 언젠가 다른 용도로 쓰인다.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN duplicate_key VARCHAR(64);

ALTER TABLE identity_verifications
    ADD COLUMN duplicate_key VARCHAR(64);

-- ⚠️ UNIQUE 를 걸지 않는다. identity_key 와 같은 이유다 (V23).
--
-- 본인인증을 받지 않은 소셜 계정이 따로 있을 수 있고, 그 계정이 나중에 본인인증을 하면
-- 같은 사람의 계정이 둘이 된다 — 설계가 허용하는 상태다(중복가입 허용). UNIQUE 를 걸면
-- 그 경로가 DB 에서 막혀, 소셜 회원이 본인인증을 받는 순간 500 이 난다.
--
-- 중복 안내는 자체 가입 때 조회로 한다 (UserAccountService.findSamePerson).
CREATE INDEX idx_users_duplicate_key ON users (duplicate_key) WHERE duplicate_key IS NOT NULL;

COMMENT ON COLUMN users.duplicate_key IS
    'DI(중복가입확인정보)의 HMAC-SHA256 해시. 이용기관별 고유값이라 외부 대조가 불가능하다. 간편인증 경로에서는 NULL';
COMMENT ON COLUMN identity_verifications.duplicate_key IS
    '인증 시점의 DI 해시. 공급자가 주지 않으면 NULL';
