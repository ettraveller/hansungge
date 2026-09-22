-- =====================================================================
-- V23 — 본인확인을 「간편인증」으로 바꾸고, 같은 사람 판정 키를 DI → CI 해시로
--
-- 2026-09-21 결정 (회원가입·본인확인 방식 변경 요청서):
--   PASS 로 가입시키려면 KG이니시스 통합인증의 「간편인증(01)」이어야 한다. 「본인확인(03)」에는
--   PASS·네이버·카카오가 없다. 간편인증은 DI 를 주지 않고 CI 를 준다.
--
--   · 같은 사람 판정 키: DI → identity_key = HMAC-SHA256(pepper, CI).
--     CI 원본은 저장하지 않는다 (이니시스 안내: 간편인증 CI 는 "비교검증 후 파기 권고").
--     pepper 는 환경변수로만 들어오고, 바꾸면 기존 회원 전원과 대조가 끊긴다.
--   · identity_key 는 UNIQUE 가 아니다. 소셜 가입자는 본인확인 없이 가입하고, 나중에 매입 등에서
--     본인인증을 받으면 같은 사람의 자체 계정과 같은 키를 가질 수 있다 (중복가입 허용).
--     자체 가입의 중복 안내는 애플리케이션이 조회로만 한다.
--   · 소셜 가입자는 본인확인을 받지 않으므로 "탈퇴하지 않은 회원은 신원 필수" 제약을 푼다.
--   · 간편인증은 성별·내외국인을 주지 않는다 — 컬럼을 지운다.
--
-- 기존 DI 는 CI 해시로 바꿀 수 없다(서로 다른 값). 기존 회원의 identity_key 는 NULL 로 남고,
-- 다음 본인인증 때 채워진다.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_identity_required;
DROP INDEX IF EXISTS ux_users_di_active;

ALTER TABLE users DROP COLUMN di;
ALTER TABLE users DROP COLUMN gender;      -- chk_users_gender 도 함께 사라진다
ALTER TABLE users DROP COLUMN foreigner;

ALTER TABLE users ADD COLUMN identity_key VARCHAR(64);
ALTER TABLE users RENAME COLUMN phone_verified_at TO identity_verified_at;

CREATE INDEX idx_users_identity_key ON users (identity_key) WHERE identity_key IS NOT NULL;

COMMENT ON COLUMN users.identity_key IS
    '같은 사람 판정 키 = HMAC-SHA256(pepper, CI). CI 원본은 저장하지 않는다. UNIQUE 아님 — 소셜 가입자가 나중에 본인인증하면 같은 값이 생길 수 있다';
COMMENT ON COLUMN users.identity_verified_at IS
    '마지막 본인인증(간편인증) 시각. 소셜 가입자는 본인인증 전까지 NULL';
COMMENT ON COLUMN users.name IS
    '자체 가입·본인인증 회원은 본인인증 값. 본인인증 전 소셜 회원은 제공자가 준 이름(검증 안 됨) 또는 NULL';


-- ---------------------------------------------------------------------
-- 2. identity_verifications — 본인인증 이력 (append-only)
--
-- 트리거 함수가 지울 컬럼(di·gender·foreigner)을 참조하므로 함수부터 바꾼다.
-- plpgsql 은 실행할 때 컬럼을 찾아서, 순서를 바꾸면 다음 UPDATE 에서야 터진다.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_identity_verification_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'identity_verifications is append-only. Rows cannot be deleted.';
    END IF;

    IF NEW.provider          IS DISTINCT FROM OLD.provider
       OR NEW.provider_tx_id IS DISTINCT FROM OLD.provider_tx_id
       OR NEW.purpose        IS DISTINCT FROM OLD.purpose
       OR NEW.identity_key   IS DISTINCT FROM OLD.identity_key
       OR NEW.name           IS DISTINCT FROM OLD.name
       OR NEW.birth_date     IS DISTINCT FROM OLD.birth_date
       OR NEW.phone          IS DISTINCT FROM OLD.phone
       OR NEW.carrier        IS DISTINCT FROM OLD.carrier
       OR NEW.verified_at    IS DISTINCT FROM OLD.verified_at
       OR NEW.expires_at     IS DISTINCT FROM OLD.expires_at
       OR NEW.ip             IS DISTINCT FROM OLD.ip THEN
        RAISE EXCEPTION 'identity_verifications identity columns are append-only.';
    END IF;

    IF OLD.user_id IS NOT NULL AND NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        RAISE EXCEPTION 'identity_verifications.user_id cannot be reassigned.';
    END IF;

    IF OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS DISTINCT FROM OLD.consumed_at THEN
        RAISE EXCEPTION 'identity_verifications.consumed_at cannot be changed once set.';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 함수가 identity_key 를 참조하므로 컬럼을 먼저 만든다 (위 함수는 실행 시점에 찾는다).
ALTER TABLE identity_verifications ADD COLUMN identity_key VARCHAR(64);

ALTER TABLE identity_verifications DROP COLUMN di;          -- idx_iv_di 도 함께 사라진다
ALTER TABLE identity_verifications DROP COLUMN gender;
ALTER TABLE identity_verifications DROP COLUMN foreigner;

-- 새 행은 키가 반드시 있어야 한다. 기존 행(DI 시절)은 키가 없으므로 검사하지 않는다(NOT VALID).
ALTER TABLE identity_verifications
    ADD CONSTRAINT chk_iv_identity_key CHECK (identity_key IS NOT NULL) NOT VALID;

CREATE INDEX idx_iv_identity_key ON identity_verifications (identity_key, verified_at DESC);

-- 목적: 가입·비밀번호 찾기에 더해 STEP_UP(로그인한 회원의 본인인증 — 매입·정산계좌 전 재확인).
ALTER TABLE identity_verifications DROP CONSTRAINT IF EXISTS identity_verifications_purpose_check;
ALTER TABLE identity_verifications
    ADD CONSTRAINT chk_iv_purpose CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'ORDER', 'STEP_UP'));

COMMENT ON TABLE  identity_verifications IS '본인인증(간편인증) 이력. 신원 컬럼은 append-only. CI 원본은 없다';
COMMENT ON COLUMN identity_verifications.identity_key IS 'HMAC-SHA256(pepper, CI). V23 이전 행(DI 시절)은 NULL';
