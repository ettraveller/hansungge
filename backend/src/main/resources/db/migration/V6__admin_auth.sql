-- =====================================================================
-- V6 — 관리자 인증/감사 (STEP 5)
--
-- V1 의 admin_users 는 status 에 'LOCKED' 값만 있고 "무엇 때문에 몇 번 실패해서
-- 잠겼는지"를 담을 곳이 없었다. 설계서 §10 의 "5회 실패 시 LOCKED" 를 구현하려면
-- 실패 횟수를 서버가 아니라 DB 에 두어야 한다 — 인스턴스 메모리에 두면 재기동으로
-- 초기화되고, 나중에 인스턴스를 늘리면 카운터가 갈라진다.
--
-- ※ 문서(TODO C-1)의 "V6 스키마 결함 12건"은 V7 로 밀린다. 인증이 먼저 필요해서
--    번호가 앞선 것뿐이고, C-1 의 내용·범위는 그대로다.
-- =====================================================================

ALTER TABLE admin_users
    -- 연속 로그인 실패 횟수. 성공하면 0 으로 되돌린다.
    ADD COLUMN login_fail_count INT NOT NULL DEFAULT 0
        CHECK (login_fail_count >= 0),

    -- 잠긴 시각. status='LOCKED' 의 근거를 남긴다 (감사 대응).
    ADD COLUMN locked_at TIMESTAMPTZ,

    -- 마지막 비밀번호 변경 시각. 최초 관리자에게 변경을 강제할 때 쓴다.
    ADD COLUMN password_changed_at TIMESTAMPTZ,

    -- 토큰 세대. 로그아웃·비밀번호 변경·상태 변경 시 +1 한다.
    -- 발급된 JWT 는 'tv' 클레임에 이 값을 담고, 불일치하면 즉시 무효가 된다.
    -- 무상태 JWT 로는 로그아웃이 실질적인 무효화가 되지 않는데, 관리자 토큰은
    -- 탈취 시 피해가 커서 그 상태를 두지 않는다.
    ADD COLUMN token_version INT NOT NULL DEFAULT 0
        CHECK (token_version >= 0);

COMMENT ON COLUMN admin_users.login_fail_count    IS '연속 로그인 실패 횟수. 임계치 도달 시 status=LOCKED';
COMMENT ON COLUMN admin_users.locked_at           IS '계정 잠금 시각';
COMMENT ON COLUMN admin_users.password_changed_at IS '마지막 비밀번호 변경 시각';
COMMENT ON COLUMN admin_users.token_version       IS 'JWT 세대. 로그아웃/비밀번호 변경 시 증가하여 기존 토큰을 무효화';

-- 기존 계정(최초 관리자)은 아직 비밀번호를 바꾼 적이 없다. NULL 로 둔다.

-- ---------- 감사로그 조회 인덱스 ----------
-- GET /api/admin/audit-logs 의 기본 검색축은 actor + 기간이다.
-- V1 이 만든 idx_audit_created / idx_audit_action / idx_audit_target 에는
-- actor 축이 없어 actor 검색이 전건 스캔으로 떨어진다.
CREATE INDEX idx_audit_actor ON audit_logs(actor, created_at DESC);
