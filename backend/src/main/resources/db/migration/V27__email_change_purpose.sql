-- =====================================================================
-- 마이페이지에서 이메일을 등록·변경할 때 쓸 인증번호 용도
--
-- 가입 때 이메일을 건너뛴 회원(소셜 가입자 전부, 그리고 자체 가입에서 이메일을
-- 비워 둔 회원)은 나중에 주소를 넣을 길이 없었다. 그 길을 여는 데 필요한 용도다.
--
-- 왜 EMAIL_VERIFY 를 재사용하지 않나 — 둘 다 "이 주소가 당신 것인가" 를 묻지만,
-- 하나는 가입 중(로그인 전)이고 하나는 로그인한 회원이 쓴다. 같은 값으로 두면
-- 한쪽에서 받은 번호가 다른 쪽에서 통한다. 용도를 나눠 두면 그 경로가 막힌다.
-- V24 가 PASSWORD_RESET 을 더할 때와 같은 방식이다.
-- =====================================================================

ALTER TABLE verification_codes DROP CONSTRAINT IF EXISTS chk_vc_purpose;
ALTER TABLE verification_codes
    ADD CONSTRAINT chk_vc_purpose
        CHECK (purpose IN ('LOGIN', 'EMAIL_VERIFY', 'PASSWORD_RESET', 'EMAIL_CHANGE'));
