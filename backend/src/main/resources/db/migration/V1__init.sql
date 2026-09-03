-- =====================================================================
-- V1__init.sql : 공통 기반 (회원, 소셜계정, 권한, 관리자, 감사로그, 설정)
-- DB: PostgreSQL 15+  /  금액 NUMERIC(15,2), 중량 NUMERIC(12,4), 시각 TIMESTAMPTZ
-- =====================================================================

-- ---------- 회원 ----------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255),
    name          VARCHAR(100),
    phone         VARCHAR(30),                     -- 앱 계층에서 암호화 저장
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','DORMANT','WITHDRAWN','BLOCKED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE social_accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id),
    provider         VARCHAR(20)  NOT NULL CHECK (provider IN ('GOOGLE','NAVER','KAKAO')),
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    connected_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_social UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_social_user ON social_accounts(user_id);

CREATE TABLE roles (
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE               -- ROLE_USER 등
);
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
INSERT INTO roles(code) VALUES ('ROLE_USER');

-- ---------- 토큰 ----------
CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(10)  NOT NULL CHECK (subject_type IN ('USER','ADMIN')),
    subject_id   BIGINT       NOT NULL,
    token_hash   VARCHAR(128) NOT NULL,            -- 원문 저장 금지, SHA-256
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_rt_subject ON refresh_tokens(subject_type, subject_id);
CREATE INDEX idx_rt_hash    ON refresh_tokens(token_hash);

-- ---------- 관리자 ----------
CREATE TABLE admin_users (
    id            BIGSERIAL PRIMARY KEY,
    login_id      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,           -- BCrypt
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'ADMIN'
                  CHECK (role IN ('SUPER_ADMIN','ADMIN','MANAGER','OPERATOR')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 초기 관리자 계정은 시드 SQL이 아니라 앱 최초 구동 시(또는 운영자 수동 INSERT)
-- BCrypt 해시로 생성한다. 평문/고정 해시를 마이그레이션에 넣지 않는다.

-- ---------- 감사로그 ----------
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    actor_type   VARCHAR(10)  NOT NULL CHECK (actor_type IN ('ADMIN','USER','SYSTEM')),
    actor        VARCHAR(100) NOT NULL,            -- login_id 또는 SYSTEM
    action       VARCHAR(60)  NOT NULL,            -- PRICE_APPLY, ORDER_STATUS_CHANGE ...
    target_type  VARCHAR(60),
    target_id    VARCHAR(60),
    before_value JSONB,
    after_value  JSONB,
    ip           VARCHAR(45),
    user_agent   VARCHAR(300),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at);
CREATE INDEX idx_audit_target  ON audit_logs(target_type, target_id);
CREATE INDEX idx_audit_action  ON audit_logs(action, created_at);

-- ---------- 시스템 설정 ----------
CREATE TABLE system_settings (
    key         VARCHAR(80)  PRIMARY KEY,
    value       TEXT         NOT NULL,
    description VARCHAR(300),
    updated_by  VARCHAR(60),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO system_settings(key, value, description) VALUES
 ('MAX_PRICE_CHANGE_PERCENT', '5',    '시세 자동적용 허용 변동률(%). 초과 시 승인 대기'),
 ('PRICE_STALE_MINUTES',      '240',  '시세 노후 판정(분). 초과 시 시세문의 표시·구매 차단'),
 ('MARKET_FETCH_CRON',        '0 0/30 10-18 * * MON-FRI', '시세 자동수집 스케줄'),
 ('PRICE_ROUNDING_MODE',      'TBD',  '[TODO A-2] 최종가 반올림 정책 (미확정)'),
 ('VAT_MODE',                 'TBD',  '[TODO A-1] 부가세 표시 정책 (미확정)'),
 ('SHIPPING_FEE',             '0',    '기본 배송비(원)'),
 ('FREE_SHIPPING_THRESHOLD',  '0',    '무료배송 기준금액(0=항상 무료)');
