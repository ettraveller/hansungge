-- =====================================================================
-- V13__self_signup_identity.sql : 자체 회원가입 + 본인확인 (이메일 → 휴대폰)
--
-- 지금까지 이 사이트는 소셜 로그인만 받았다. 근거는 개발계획 §7.1 의 한 줄이었다 —
-- "자체 ID/PW 회원가입 없음 → 비밀번호 유출 리스크 원천 제거".
--
-- 그 결정을 뒤집는다. 소셜 계정이 없거나 쓰기 싫은 고객을 놓치고 있고, 무엇보다
-- <b>귀금속 거래는 실명과 연락처가 필요한 업종</b>인데 지금 users.phone 은 마이페이지에서
-- 손으로 넣는 검증되지 않은 선택 입력값이다. 매입 신청도 고액 주문도 그 값 위에 설 수 없다.
--
-- 잃는 것(비밀번호 보관 리스크)은 관리자 계정에 이미 쓰고 있는 BCrypt cost 12 +
-- 실패 잠금 + Rate Limit 구조를 회원에 그대로 적용해 상쇄한다.
--
-- <h2>가입 시점에 인증을 건다</h2>
-- 이메일 인증과 휴대폰 본인확인을 <b>둘 다, 가입 시</b> 요구한다. 결제 단계에서 마찰을
-- 만들지 않기 위해서고, 그 결과가 매입 신청에서 재사용된다.
--
-- 소셜 가입도 예외가 아니다. 한쪽만 면제하면 "본인확인을 안 거친 회원"이라는 상태가
-- 생기고, 그 상태 하나를 위한 예외 분기가 주문·매입·이벤트 코드 전체에 번진다.
--
-- <h2>CI 는 저장하지 않는다</h2>
-- 2024-08-14 시행 정보통신망법 §23조의5·6 으로 CI 이용기관에 직접 의무와 형벌이
-- 생겼다(목적 외 사용 5년 이하 징역). 중복가입 방지에는 DI 로 충분하다 —
-- DI 는 사이트별 고유값이라 다른 사업자와 연계되지 않는다.
--
-- <b>그래서 이 마이그레이션에는 ci 컬럼이 없다.</b> 컬럼이 있으면 언젠가 채워진다.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. users — 비밀번호와 본인확인 결과를 받을 자리
--
-- 이름·생년월일·성별·내외국인·휴대폰은 <b>본인확인 응답으로만</b> 채워지고 회원이
-- 고칠 수 없다. 고칠 수 있으면 본인확인을 한 의미가 없어진다 — "확인된 값"이
-- 아니라 "한 번 확인했다가 지금은 모르는 값"이 된다.
--
-- password_hash 가 NULL 인 회원이 정상적으로 존재한다. 소셜로만 가입한 사람이다.
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN password_hash     VARCHAR(100),
    ADD COLUMN birth_date        DATE,
    ADD COLUMN gender            VARCHAR(10),
    ADD COLUMN foreigner         BOOLEAN,
    ADD COLUMN di                VARCHAR(88),
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN phone_verified_at TIMESTAMPTZ,
    ADD COLUMN login_fail_count  INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_at         TIMESTAMPTZ,
    ADD COLUMN password_changed_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE')),
    ADD CONSTRAINT chk_users_fail_count CHECK (login_fail_count >= 0);

COMMENT ON COLUMN users.password_hash IS 'BCrypt cost 12. 소셜로만 가입한 회원은 NULL 이다';
COMMENT ON COLUMN users.di            IS '본인확인기관이 발급한 중복가입확인정보. 사이트별 고유값이라 타 사업자와 연계되지 않는다. CI 는 저장하지 않는다';
COMMENT ON COLUMN users.birth_date    IS '본인확인 응답으로만 채운다. 만 14세 판정 근거';
COMMENT ON COLUMN users.locked_at     IS '연속 로그인 실패로 잠긴 시각. admin_users 와 같은 규칙';

-- 이메일은 지금 인덱스만 있고 UNIQUE 가 아니다 (V1__init.sql:17). 소셜 전용이던
-- 시절에는 제공자마다 별도 계정을 허용했기 때문이다. 이메일로 로그인하려면
-- 이메일이 계정을 하나로 지목해야 한다.
--
-- 부분 인덱스인 이유 — 탈퇴한 회원의 행은 남는다(status=WITHDRAWN). 그 이메일을
-- 영영 못 쓰게 만들면, 탈퇴한 본인조차 같은 주소로 다시 가입할 수 없다.
--
-- lower() 로 거는 이유 — 대소문자만 다른 주소로 두 계정을 만들 수 있으면
-- 중복가입 방지가 뚫린다. 조회도 반드시 lower(email) 로 해야 이 인덱스를 탄다.
CREATE UNIQUE INDEX ux_users_email_active ON users (lower(email))
    WHERE status <> 'WITHDRAWN' AND email IS NOT NULL;

-- DI 는 중복가입 방지의 최종 방어선이다. 애플리케이션에서 먼저 검사하지만,
-- 동시에 들어온 두 요청은 검사와 저장 사이를 비집고 들어온다. DB 가 막는다.
CREATE UNIQUE INDEX ux_users_di_active ON users (di)
    WHERE status <> 'WITHDRAWN' AND di IS NOT NULL;


-- ---------------------------------------------------------------------
-- 2. identity_verifications — 휴대폰 본인확인 이력
--
-- <h2>왜 users 에 컬럼만 더하지 않고 별도 테이블인가</h2>
-- 본인확인은 <b>가입 전에</b> 일어난다. 아직 회원이 아닌 사람의 확인 결과를 담을
-- 곳이 필요하다. 그리고 한 회원이 여러 번 본인확인을 한다 — 가입할 때, 비밀번호를
-- 잊었을 때, 고액 주문 전에. 그 각각이 언제 무슨 목적으로 이루어졌는지가 남아야
-- 분쟁에서 증명이 된다.
--
-- <h2>왜 append-only 인가</h2>
-- 약관 동의 이력과 같은 이유다. "그때 이 사람이 본인확인을 통과했다"는 사실은
-- 나중에 고쳐 쓸 수 있으면 증거가 아니다. user_agreements 는 전면 거부형
-- 트리거지만 이 테이블은 그럴 수 없다 — 가입이 끝나면 user_id 를 채워야 하고,
-- 한 번 쓴 인증은 consumed_at 으로 소진 표시를 해야 한다. 그래서 <b>신원 컬럼만</b>
-- 잠그는 비교형 트리거를 쓴다.
--
-- ⚠️ ci 컬럼은 없다. 위 머리말 참조.
-- ---------------------------------------------------------------------
CREATE TABLE identity_verifications (
    id             BIGSERIAL PRIMARY KEY,
    provider       VARCHAR(20)  NOT NULL,
    provider_tx_id VARCHAR(100) NOT NULL,
    purpose        VARCHAR(20)  NOT NULL
                   CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'ORDER')),
    di             VARCHAR(88)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    birth_date     DATE         NOT NULL,
    gender         VARCHAR(10)  NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    foreigner      BOOLEAN      NOT NULL,
    phone          VARCHAR(30)  NOT NULL,
    carrier        VARCHAR(20),
    user_id        BIGINT REFERENCES users (id),
    consumed_at    TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ  NOT NULL,
    verified_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip             VARCHAR(45),

    -- 같은 거래번호가 두 번 들어올 수 없다. 공급자 응답을 가로채 재전송하는
    -- 방식으로 남의 본인확인 결과를 자기 가입에 쓰는 경로를 막는다.
    CONSTRAINT ux_iv_tx UNIQUE (provider, provider_tx_id)
);

CREATE INDEX idx_iv_di   ON identity_verifications (di, verified_at DESC);
CREATE INDEX idx_iv_user ON identity_verifications (user_id, verified_at DESC);

COMMENT ON TABLE  identity_verifications        IS '휴대폰 본인확인 이력. 신원 컬럼은 append-only';
COMMENT ON COLUMN identity_verifications.provider_tx_id IS '공급자 거래번호. 분쟁 시 공급자 기록과 대조하는 유일한 열쇠';
COMMENT ON COLUMN identity_verifications.user_id        IS '가입 완료 시 연결한다. 가입 전에는 NULL';
COMMENT ON COLUMN identity_verifications.consumed_at    IS '이 인증을 써서 가입·재설정을 마친 시각. 한 번 쓴 인증은 재사용할 수 없다';

-- 신원 컬럼만 잠근다. user_id 와 consumed_at 은 가입 완료 시 채워야 하므로 연다.
-- 한 번 채워진 뒤에 다시 바뀌는 것도 막는다 — 소진된 인증을 되살려 두 번 쓰는 길이 된다.
CREATE OR REPLACE FUNCTION fn_identity_verification_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'identity_verifications is append-only. Rows cannot be deleted.';
    END IF;

    IF NEW.provider       IS DISTINCT FROM OLD.provider
       OR NEW.provider_tx_id IS DISTINCT FROM OLD.provider_tx_id
       OR NEW.purpose     IS DISTINCT FROM OLD.purpose
       OR NEW.di          IS DISTINCT FROM OLD.di
       OR NEW.name        IS DISTINCT FROM OLD.name
       OR NEW.birth_date  IS DISTINCT FROM OLD.birth_date
       OR NEW.gender      IS DISTINCT FROM OLD.gender
       OR NEW.foreigner   IS DISTINCT FROM OLD.foreigner
       OR NEW.phone       IS DISTINCT FROM OLD.phone
       OR NEW.carrier     IS DISTINCT FROM OLD.carrier
       OR NEW.verified_at IS DISTINCT FROM OLD.verified_at
       OR NEW.expires_at  IS DISTINCT FROM OLD.expires_at
       OR NEW.ip          IS DISTINCT FROM OLD.ip THEN
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

CREATE TRIGGER trg_identity_verification_guard
    BEFORE UPDATE OR DELETE ON identity_verifications
    FOR EACH ROW EXECUTE FUNCTION fn_identity_verification_guard();


-- ---------------------------------------------------------------------
-- 3. email_verifications — 이메일 인증 코드
--
-- 코드 원문을 저장하지 않는다. refresh_tokens 와 같은 이유이고 같은 방식(SHA-256)이다.
-- BCrypt 를 쓰지 않는 이유도 같다 — 조회 키여야 하고, 만료 5분짜리 6자리 숫자에
-- 매번 BCrypt 비용을 치를 이유가 없다.
--
-- attempt_count 가 있는 이유 — 6자리 숫자는 100만 분의 1이지만, 시도를 막지 않으면
-- 만료 전에 다 훑을 수 있다. 서비스 계층이 이 값으로 차단한다.
-- ---------------------------------------------------------------------
CREATE TABLE email_verifications (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(128) NOT NULL,
    purpose       VARCHAR(20)  NOT NULL
                  CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'CHANGE_EMAIL')),
    attempt_count INT          NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    expires_at    TIMESTAMPTZ  NOT NULL,
    verified_at   TIMESTAMPTZ,
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip            VARCHAR(45)
);

CREATE INDEX idx_ev_lookup ON email_verifications (lower(email), purpose, created_at DESC);

COMMENT ON COLUMN email_verifications.code_hash IS 'SHA-256. 원문을 저장하면 DB 를 읽을 수 있는 사람이 남의 가입을 가로챌 수 있다';
COMMENT ON COLUMN email_verifications.consumed_at IS '이 인증으로 가입·재설정을 마친 시각. 한 번 쓴 코드는 재사용할 수 없다';


-- ---------------------------------------------------------------------
-- 4. 기존 회원 정리
--
-- 개발 중 만들어진 계정만 있다. 새 구조는 <b>모든 회원이 검증된 이메일과 DI 를
-- 갖는다</b>는 전제 위에 서 있고, 그 전제 덕분에 "미인증 회원" 분기가 코드 어디에도
-- 없다. 전제를 만족하지 않는 행을 하나 남기면 그 행 하나를 위한 예외가 주문·매입·
-- 이벤트 전체에 번진다. 소급으로 본인확인을 받아 채울 수도 없다 — 본인확인은 그
-- 사람이 직접 해야 한다.
--
-- <h2>주문이 있으면 실행하지 않는다</h2>
-- 이 마이그레이션은 회원과 그에 딸린 행을 지운다. 주문 기록은 전자상거래법상 5년
-- 보존 의무가 있어 <b>어떤 경우에도 조용히 지워서는 안 된다.</b> 한 건이라도 있으면
-- 여기서 멈추고 사람이 판단하게 한다 — 지우는 것보다 배포가 멈추는 편이 낫다.
-- ---------------------------------------------------------------------
DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM orders) THEN
        RAISE EXCEPTION '주문 기록이 있는 DB 에서는 회원 초기화를 실행할 수 없습니다. '
                        '전자상거래법상 거래 기록은 5년 보존 대상입니다. '
                        '이 마이그레이션의 4절을 상황에 맞게 다시 작성하세요.';
    END IF;
END $guard$;

-- 참조의 역순으로 지운다. append-only 트리거가 걸린 동의 이력은 잠시 내린다 —
-- 운영에서는 하지 않는 일이라 이 한 번을 위해서만 연다.
ALTER TABLE user_agreements DISABLE TRIGGER trg_user_agreement_guard;

DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM coupon_issues;
DELETE FROM event_entries;
DELETE FROM qnas;
DELETE FROM user_agreements;
DELETE FROM refresh_tokens WHERE subject_type = 'USER';
DELETE FROM social_accounts;
DELETE FROM user_roles;
DELETE FROM users;

ALTER TABLE user_agreements ENABLE TRIGGER trg_user_agreement_guard;
