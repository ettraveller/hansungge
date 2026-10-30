-- =====================================================================
-- V32__shipment.sql : 배송(송장) 관리
--
-- 2026-10-30. shipments 표는 V4 에서 자리만 잡아 두고 아무도 쓰지 않았다.
-- 관리자가 송장번호를 넣고 발송·수령을 기록할 수 있게 채운다.
--
-- ---------------------------------------------------------------------
-- 택배사는 우체국택배 하나다
--
-- 컬럼은 남겨 둔다. 한 곳만 쓴다고 값을 비워 두면 나중에 택배사가 늘 때
-- "이 행은 어디로 보낸 것인가"를 알 수 없고, 송장번호 형식도 택배사마다 다르다.
--
-- ---------------------------------------------------------------------
-- 왜 주문당 한 건인가
--
-- 부분 발송(한 주문을 두 번에 나눠 보내기)을 지금 열지 않는다. 귀금속은 한 번에
-- 보내고, 열어 두면 "어느 품목이 어느 송장에 실렸는가"를 품목마다 적어야 한다.
-- 필요해지면 shipment_items 를 더하는 쪽이 맞지, 주문당 여러 행을 허용할 일이
-- 아니다 — UNIQUE 로 지금 막아 둔다.
-- =====================================================================

-- 상태값을 넓힌다. 기존 CHECK 는 PENDING/SHIPPED/DELIVERED 뿐이라
-- "집화 전"과 "배송 출발"을 구분할 수 없다.
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_status_check;

ALTER TABLE shipments
    ADD CONSTRAINT chk_shipment_status CHECK (status IN (
        'PENDING',      -- 송장번호가 아직 없다
        'SHIPPED',      -- 송장번호를 넣고 보냈다
        'IN_TRANSIT',   -- 택배사가 이동 중이라고 알려 왔다
        'DELIVERED'     -- 수령
    ));

ALTER TABLE shipments
    ALTER COLUMN carrier SET DEFAULT 'KOREA_POST';

-- 주문당 한 건. 중복 발송 기록이 생기면 고객 화면에 송장번호가 둘 보인다.
CREATE UNIQUE INDEX IF NOT EXISTS ux_shipment_order ON shipments (order_id);

-- 같은 송장번호를 두 주문에 넣는 실수를 막는다. 택배사가 다르면 번호가 겹칠 수
-- 있으므로 (택배사, 번호) 쌍으로 본다. 번호가 없는 행은 제외한다.
CREATE UNIQUE INDEX IF NOT EXISTS ux_shipment_tracking
    ON shipments (carrier, tracking_no)
    WHERE tracking_no IS NOT NULL;

-- 배송조회 API 가 알려 준 내용을 담는다.
--
-- 조회할 때마다 외부를 부르면 상세 페이지를 열 때마다 요청이 나가고(비용·지연),
-- 택배사가 느릴 때 화면이 같이 느려진다. 받아 온 것을 여기 두고 화면은 이것만 읽는다.
ALTER TABLE shipments
    ADD COLUMN tracking_synced_at TIMESTAMPTZ,
    ADD COLUMN tracking_status    VARCHAR(40),
    ADD COLUMN tracking_detail    JSONB;

COMMENT ON COLUMN shipments.carrier IS '택배사 코드. 지금은 KOREA_POST 하나뿐이다';
COMMENT ON COLUMN shipments.tracking_synced_at IS
    '배송조회 API 를 마지막으로 부른 시각. NULL 이면 아직 부른 적 없다(키 미설정 포함)';
COMMENT ON COLUMN shipments.tracking_detail IS
    '택배사가 준 진행 단계 목록. 화면은 이것을 그린다 — 외부를 다시 부르지 않는다';

-- 발송 대기 목록을 자주 읽는다 (관리자 주문 목록의 "발송 대기 N건").
CREATE INDEX IF NOT EXISTS idx_shipment_status ON shipments (status, order_id);
