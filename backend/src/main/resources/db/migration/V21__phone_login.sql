-- =====================================================================
-- V21__phone_login.sql : 로그인 ID 를 휴대폰번호로 · 본인확인 필수
--
-- 로그인 ID 가 이메일에서 <b>휴대폰번호</b>로 바뀐다. 이메일은 세금계산서·거래명세서를
-- 받을 선택 항목이 된다.
--
-- 휴대폰번호는 본인확인이 준 값이라 명의가 검증돼 있다. 그 번호로 로그인하게 하면
-- 비밀번호를 잊은 사람도 SMS 인증번호로 들어올 수 있고, 매입 대금을 보낼 때 예금주와
-- 맞춰 볼 근거도 같은 값이다.
--
-- <h2>같은 사람 판정은 여전히 DI 다</h2>
-- 휴대폰번호는 <b>사람이 아니라 회선</b>에 붙는다. 해지된 번호는 다른 사람에게 다시
-- 나간다. 번호로 "이미 가입한 사람인가"를 판정하면, 번호를 물려받은 사람이 앞사람의
-- 계정(정산계좌 포함)으로 들어가거나 가입 자체를 못 한다. 그래서:
--
--   DI    — 같은 사람인가 (중복가입 차단, 소셜 연결)       ux_users_di_active
--   phone — 어느 계정으로 로그인하는가 (로그인 ID)        ux_users_phone_active
--
-- 둘이 어긋나는 순간(다른 DI 가 이미 쓰이는 번호로 본인확인)이 곧 번호 재할당이다.
-- 그때는 옛 계정의 번호를 비운다 — 그래서 phone 은 NULL 을 허용한다.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 본인확인 없이 가입한 회원 정리
--
-- 본인확인 공급자 계약 전에 한시적으로 열어 둔 소셜 가입(User#signUpWithoutIdentity)
-- 으로 만들어진 행이다. 이 행들에는 이름·생년월일·휴대폰이 없어 휴대폰 로그인의
-- 대상이 될 수 없고, 소급으로 채울 수도 없다 — 본인확인은 그 사람이 직접 해야 한다.
--
-- <h2>주문이 있으면 실행하지 않는다</h2>
-- V13 4절과 같은 이유다. 거래 기록은 전자상거래법상 5년 보존 대상이라 조용히 지울 수
-- 없다. 한 건이라도 있으면 멈추고 사람이 판단하게 한다.
-- ---------------------------------------------------------------------
DO $guard$
BEGIN
    IF EXISTS (SELECT 1
               FROM orders o
               JOIN users u ON u.id = o.user_id
               WHERE u.di IS NULL) THEN
        RAISE EXCEPTION '본인확인 없이 가입한 회원 중 주문 기록이 있는 회원이 있습니다. '
                        '거래 기록은 5년 보존 대상이라 자동으로 지울 수 없습니다. '
                        'V21 1절을 상황에 맞게 다시 작성하세요.';
    END IF;

    -- 본인확인 이력은 삭제할 수 없는 테이블이다(append-only). 여기에 걸린 행이 있으면
    -- 회원을 지울 수 없으므로 원인을 알 수 있게 먼저 멈춘다. 정상이라면 0 건이다 —
    -- 이 회원들은 본인확인을 한 적이 없다.
    IF EXISTS (SELECT 1
               FROM identity_verifications iv
               JOIN users u ON u.id = iv.user_id
               WHERE u.di IS NULL) THEN
        RAISE EXCEPTION '본인확인 이력이 연결된 DI 없는 회원이 있습니다. 데이터를 먼저 확인하세요.';
    END IF;
END $guard$;

CREATE TEMP TABLE v21_unverified ON COMMIT DROP AS
    SELECT id FROM users WHERE di IS NULL;

-- 참조의 역순으로 지운다. 동의 이력의 append-only 트리거는 이 한 번을 위해서만 잠시 내린다.
ALTER TABLE user_agreements DISABLE TRIGGER trg_user_agreement_guard;

DELETE FROM cart_items
    WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN (SELECT id FROM v21_unverified));
DELETE FROM carts          WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM coupon_issues  WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM event_entries  WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM qnas           WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM user_agreements WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM refresh_tokens
    WHERE subject_type = 'USER' AND subject_id IN (SELECT id FROM v21_unverified);
DELETE FROM social_accounts WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM user_roles     WHERE user_id IN (SELECT id FROM v21_unverified);
DELETE FROM users          WHERE id IN (SELECT id FROM v21_unverified);

ALTER TABLE user_agreements ENABLE TRIGGER trg_user_agreement_guard;


-- ---------------------------------------------------------------------
-- 2. 휴대폰번호를 한 가지 모양으로
--
-- 로그인 ID 는 비교되는 값이다. '010-1234-5678' 과 '01012345678' 이 다른 계정을
-- 가리키면 유일성이 뚫린다. 숫자만 남긴다. 애플리케이션도 저장·조회 전에 같은 규칙으로
-- 정규화한다 (PhoneNumber).
--
-- identity_verifications.phone 은 건드리지 않는다 — 본인확인 당시 받은 원문 그대로가
-- 증거이고, 그 테이블은 신원 컬럼이 append-only 다.
-- ---------------------------------------------------------------------
UPDATE users
   SET phone = regexp_replace(phone, '[^0-9]', '', 'g')
 WHERE phone IS NOT NULL
   AND phone ~ '[^0-9]';

UPDATE users SET phone = NULL WHERE phone = '';

-- 같은 번호를 쥔 활성 계정이 둘 이상이면 가장 최근 계정만 번호를 갖는다.
-- 번호 재할당 규칙(앞 계정의 번호를 비운다)과 같은 결론이다. 운영에서는 0 건이어야
-- 하고, 개발 DB 에서는 Mock 본인확인의 무작위 번호가 겹쳐 생길 수 있다.
UPDATE users u
   SET phone = NULL
 WHERE u.phone IS NOT NULL
   AND u.status <> 'WITHDRAWN'
   AND EXISTS (SELECT 1 FROM users newer
               WHERE newer.phone = u.phone
                 AND newer.status <> 'WITHDRAWN'
                 AND newer.id > u.id);


-- ---------------------------------------------------------------------
-- 3. 본인확인 값은 필수
--
-- NOT NULL 이 아니라 조건부 CHECK 로 건다. 탈퇴 회원의 개인정보는 보존기간이 지나면
-- 파기해야 하는데(STEP 18 파기 배치), NOT NULL 이면 그 배치가 이 제약에 막힌다.
--
-- phone 은 여기 넣지 않는다 — 번호 재할당 때 비워야 하기 때문이다(머리말 참조).
-- identity_verified_at 을 따로 만들지 않는다. phone_verified_at 이 같은 뜻이다.
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN carrier VARCHAR(20);

COMMENT ON COLUMN users.carrier IS '본인확인 응답의 통신사. 마지막 본인확인 기준';
COMMENT ON COLUMN users.phone   IS '로그인 ID. 숫자만(01012345678). 번호 재할당 시 옛 계정은 NULL 이 된다';

ALTER TABLE users
    ADD CONSTRAINT chk_users_identity_required CHECK (
        status = 'WITHDRAWN'
        OR (di IS NOT NULL
            AND name IS NOT NULL
            AND birth_date IS NOT NULL
            AND phone_verified_at IS NOT NULL));

ALTER TABLE users
    ADD CONSTRAINT chk_users_phone_digits CHECK (phone IS NULL OR phone ~ '^[0-9]{10,11}$');


-- ---------------------------------------------------------------------
-- 4. 로그인 ID 유일성
--
-- 탈퇴한 행은 제외한다 — 이메일·DI 인덱스와 같은 이유로, 탈퇴한 본인이 같은 번호로
-- 다시 가입할 수 있어야 한다. 애플리케이션이 먼저 검사하지만 동시에 들어온 두 요청은
-- 그 틈을 비집고 들어온다. 마지막은 DB 가 막는다.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX ux_users_phone_active ON users (phone)
    WHERE status <> 'WITHDRAWN' AND phone IS NOT NULL;
