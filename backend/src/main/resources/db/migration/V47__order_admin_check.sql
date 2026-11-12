-- V47: 주문의 관리자 확인 시각 (2026-11-13)
--
-- 관리자가 주문 상세를 연 적이 있는가. NULL 이면 아직 아무도 열어보지 않은 주문이고,
-- 주문 관리 화면의 상태 탭마다 이 미확인 건수를 배지로 보여 준다.
--
-- 지나간 주문을 소급해 채우지 않는다 — 열어본 적 없는 주문을 열어봤다고 적는 것이다.
-- 도입 직후 배지가 기존 주문 수만큼 떠 있는 것이 맞는 상태다.

ALTER TABLE orders
    ADD COLUMN admin_checked_at TIMESTAMPTZ;

COMMENT ON COLUMN orders.admin_checked_at IS
    '관리자가 상세를 처음 연 시각. NULL = 미확인. 카드 결제 도착·고객 취소 때 NULL 로 돌아간다';

-- 미확인 건수는 화면을 열 때마다 센다. 운영이 쌓이면 확인된 주문이 대부분이라,
-- 미확인만 담는 부분 인덱스로 두면 인덱스가 작게 유지된다.
CREATE INDEX idx_orders_admin_unchecked
    ON orders (status)
    WHERE admin_checked_at IS NULL;
