-- =====================================================================
-- V36 — 무통장입금(계좌입금) 결제
--
-- 왜 필요한가
--   토스페이먼츠 입점 심사(2026-11-03 회신)에서 아래 상품이 카드 결제 대상에서
--   제외됐다. 카드사가 온라인 거래를 제한하는 품목이라 심사 자체가 통과되지 않는다.
--
--     · 순금(24K) · 순은 · 골드바 등 가공되지 않은 '바' 형태
--     · 24K 상품 전부
--     · 시세에 연동되어 판매가가 변동하는 상품
--
--   이 상품들은 계좌로 직접 입금받아 판다. 금은방의 통상적인 방식이기도 하다.
--
-- 어떤 상품이 어느 수단인가 — <b>컬럼을 두지 않는다</b>
--   결제수단을 상품마다 고르게 하면, 골드바를 카드 가능으로 잘못 등록하는 순간
--   심사 위반이 된다. 그래서 금종(metal_type)과 가격방식(price_type)에서
--   <b>파생</b>시킨다. 코드 한 곳(Product#isCardPayable)에만 있고 운영자가 만질
--   수 없다 — 토스에 약속한 내용이 그것이다.
--
--     카드·간편결제 가능 = (18K 또는 14K) AND 시세 비연동(고정가·관리자지정가)
--     그 외 전부        = 무통장 전용
--
-- 자동취소를 두지 않는다
--   입금기한은 <b>안내</b>일 뿐이다. 기한이 지나도 서버는 아무것도 하지 않는다.
--   계좌를 열어 입금하려는 사이에 주문이 사라지면 그 돈이 갈 곳을 잃는다 —
--   들어온 입금을 사람이 찾아 되돌려야 하고, 그 사이 고객은 떼인 줄 안다.
--   기한이 지난 주문은 관리자 목록에 모아 보여 주고, 취소는 사람이 누른다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 주문에 결제수단과 입금 정보를 둔다
-- ---------------------------------------------------------------------

-- 고객이 고른 결제수단. 지나간 주문은 전부 카드/간편결제였다.
ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'EASY_PAY';

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_payment_method
        CHECK (payment_method IN ('EASY_PAY', 'BANK_TRANSFER'));

-- 입금자명. 통장에 찍히는 이름이라 주문자 이름과 다를 수 있다 — 남편 이름으로
-- 부치는 경우가 흔하다. 이 값이 없으면 들어온 돈이 어느 주문인지 사람이 찾아야 한다.
ALTER TABLE orders
    ADD COLUMN depositor_name VARCHAR(60);

-- 입금 안내 기한. 강제하지 않는다(위 주석) — 화면에 적어 주기 위한 값이다.
ALTER TABLE orders
    ADD COLUMN deposit_due_at TIMESTAMPTZ;

-- 무통장 주문에는 입금자명이 반드시 있어야 한다. 없으면 입금 대조가 불가능하다.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_depositor_name
        CHECK (payment_method <> 'BANK_TRANSFER' OR depositor_name IS NOT NULL);

COMMENT ON COLUMN orders.payment_method IS 'EASY_PAY(카드·간편결제) / BANK_TRANSFER(무통장입금)';
COMMENT ON COLUMN orders.depositor_name IS '통장에 찍히는 입금자명. 주문자와 다를 수 있다';
COMMENT ON COLUMN orders.deposit_due_at IS '입금 안내 기한. 지나도 자동취소하지 않는다';

-- 관리자가 「입금 대기」를 모아 보는 화면의 조회 경로.
-- 부분 인덱스다 — 무통장 주문은 전체의 일부이고, 지나간 주문까지 담을 이유가 없다.
CREATE INDEX ix_orders_bank_transfer_pending
    ON orders (deposit_due_at, id)
    WHERE payment_method = 'BANK_TRANSFER'
      AND status IN ('ORDER_CREATED', 'PAYMENT_PENDING');

-- ---------------------------------------------------------------------
-- 2. 입금받을 계좌
-- ---------------------------------------------------------------------
--
-- 설정으로 둔다. 계좌가 바뀔 때 배포하지 않아도 되게 — 은행 계좌는 코드보다
-- 자주 바뀌고, 바뀐 날 배포가 막혀 있으면 그날 주문이 엉뚱한 곳으로 간다.
--
-- ⚠️ 빈 값으로 넣는다. 실제 계좌번호는 관리자 화면에서 넣는다 — git 에 올리지 않는다.
--    계좌가 비어 있으면 서비스가 무통장 주문을 거절한다(입금할 곳이 없으므로).

INSERT INTO system_settings(key, value, description) VALUES
 ('BANK_ACCOUNT_BANK',   '', '무통장입금 받을 은행명 (예: 국민은행)'),
 ('BANK_ACCOUNT_NUMBER', '', '무통장입금 받을 계좌번호'),
 ('BANK_ACCOUNT_HOLDER', '', '무통장입금 받을 예금주'),
 ('DEPOSIT_DUE_HOURS',   '3', '입금 안내 기한(시간). 안내용이며 자동취소하지 않는다')
ON CONFLICT (key) DO NOTHING;
