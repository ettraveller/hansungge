-- =====================================================================
-- V28__delivery_addresses.sql : 배송지 주소록
--
-- 2026-10-04. 주문할 때마다 주소를 처음부터 입력하게 두고 있었다. 재구매 고객에게는
-- 그것이 가장 크게 체감되는 마찰이다.
--
-- 주문과의 관계 — 주문은 받는 사람·주소를 <b>자기 칸에 복사해</b> 가진다(orders 의
-- receiver_name·zipcode·address1…). 그래서 여기 있는 줄을 고치거나 지워도 지나간
-- 주문의 배송지는 바뀌지 않는다. 바뀌면 안 된다 — 어디로 보냈는지가 분쟁의 근거다.
-- 이 표는 "다음에 쓸 주소"만 들고 있는 편의 장치다.
--
-- 그래서 회원이 탈퇴하면 함께 지운다(ON DELETE CASCADE). 거래 기록이 아니므로
-- 보존 의무가 없고, 남겨 두면 쓸 사람이 없는 개인정보만 남는다.
-- =====================================================================

CREATE TABLE delivery_addresses (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- 별칭("집", "회사"). 목록에서 고를 때 주소를 다 읽지 않아도 되게 한다.
    label          VARCHAR(30),
    receiver_name  VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(30)  NOT NULL,
    zipcode        VARCHAR(10)  NOT NULL,
    address1       VARCHAR(200) NOT NULL,
    address2       VARCHAR(200),
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 기본 배송지는 회원당 하나다.
--
-- 부분 UNIQUE 로 거는 이유 — 애플리케이션에서 "먼저 전부 false 로 내리고 하나를 true 로"
-- 하는 것만 믿으면, 두 요청이 겹쳤을 때 둘 다 true 가 된다. 그러면 주문서가 어느 것을
-- 골라야 할지 모르고, 증상은 "가끔 엉뚱한 주소가 뜬다"로 나타나 재현이 안 된다.
CREATE UNIQUE INDEX ux_delivery_default ON delivery_addresses (user_id) WHERE is_default;

CREATE INDEX idx_delivery_user ON delivery_addresses (user_id, created_at DESC);

COMMENT ON TABLE delivery_addresses IS '배송지 주소록. 주문은 주소를 복사해 가지므로 여기를 고쳐도 지난 주문은 그대로다';
COMMENT ON COLUMN delivery_addresses.is_default IS '기본 배송지. 회원당 하나 (부분 UNIQUE)';
