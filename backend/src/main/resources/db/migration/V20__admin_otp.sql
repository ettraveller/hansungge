-- =====================================================================
-- V20 — 관리자 2단계 인증 (OTP)
--
-- 관리자 화면은 시세를 바꾸는 곳이고, 금값이 급변하면 휴대폰으로 바로 고쳐야 한다.
-- 그래서 IP 허용목록으로 "어디서"를 막을 수 없다(휴대폰 IP 는 계속 바뀐다).
-- 대신 "누가"를 막는다 — 비밀번호 다음에 휴대폰 인증 앱의 6자리를 요구한다.
--
--   otp_secret      인증 앱과 나눠 가진 키. 평문이 아니라 AES-GCM 암호문이다.
--                   DB 가 새도 키가 함께 새지 않게 한다 (애플리케이션 AdminOtpCipher).
--   otp_enabled_at  등록을 마친 시각. NULL 이면 미등록 — 등록 중에는 키만 있고 이 값은 없다.
--   otp_last_step   마지막으로 통과한 30초 구간. 같은 6자리를 두 번 쓰지 못하게 한다.
--   otp_fail_count  OTP 연속 실패. 비밀번호 실패(login_fail_count)와 따로 센다 —
--                   함께 세면 비밀번호를 아는 사람이 로그인을 반복해 카운터를 계속
--                   되돌리며 6자리를 무한히 찍어 볼 수 있다.
-- =====================================================================

ALTER TABLE admin_users
    ADD COLUMN otp_secret     VARCHAR(200),
    ADD COLUMN otp_enabled_at TIMESTAMPTZ,
    ADD COLUMN otp_last_step  BIGINT,
    ADD COLUMN otp_fail_count INT NOT NULL DEFAULT 0
        CHECK (otp_fail_count >= 0);

-- 켜져 있으면 키가 반드시 있다. 키 없이 켜진 계정은 아무도 로그인할 수 없다.
ALTER TABLE admin_users
    ADD CONSTRAINT chk_admin_otp_secret
        CHECK (otp_enabled_at IS NULL OR otp_secret IS NOT NULL);

COMMENT ON COLUMN admin_users.otp_secret     IS 'OTP 키 암호문 (AES-GCM). 평문 저장 금지';
COMMENT ON COLUMN admin_users.otp_enabled_at IS 'OTP 등록 완료 시각. NULL = 미등록';
COMMENT ON COLUMN admin_users.otp_last_step  IS '마지막으로 통과한 TOTP 30초 구간 (재사용 차단)';
COMMENT ON COLUMN admin_users.otp_fail_count IS 'OTP 연속 실패 횟수. 비밀번호 실패와 별도';


-- ---------- 복구 코드 ----------

-- 휴대폰을 잃어버렸을 때 인증 앱 대신 쓰는 1회용 코드. 평문이 아니라 HMAC 값만 둔다.
-- 코드가 40비트 무작위라 느린 해시(BCrypt)가 필요 없고, 로그인마다 10개를 BCrypt 로
-- 대조하면 그 자체가 서버를 멈추는 공격 경로가 된다.
--
-- 계정을 지우는 경로는 애플리케이션에 없지만(정지만 한다), 테스트 정리와 수동 조치에서
-- 참조 때문에 막히지 않게 함께 지운다.
CREATE TABLE admin_otp_recovery_codes (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT       NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    code_digest VARCHAR(64)  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_admin_otp_recovery UNIQUE (admin_id, code_digest)
);

CREATE INDEX idx_admin_otp_recovery_unused
    ON admin_otp_recovery_codes (admin_id)
    WHERE used_at IS NULL;

COMMENT ON TABLE admin_otp_recovery_codes IS '관리자 OTP 복구 코드 (1회용, HMAC 저장)';
