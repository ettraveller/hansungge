-- =====================================================================
-- V15 — 상품 가격 방식에 "시세문의"를 더한다
--
-- 상품마다 셋 중 하나를 고른다:
--   자동     고시 시세를 따라 값이 바뀐다     (MARKET_LINKED / PLUS / MULTIPLIER)
--   수동     사람이 정한 값으로 고정된다      (FIXED / MANUAL)
--   시세문의 값을 내걸지 않고 문의로만 판다   (INQUIRY)   ← 이번에 추가
--
-- 왜 필요한가 — 지금까지 "시세문의"는 <b>사고의 결과</b>였다. 시세가 없거나
-- 오래되면 값을 0 으로 만드는 대신 물러나는 자리다(원칙 P6). 그런데 값을
-- 일부러 안 내거는 상품도 있다. 고가 주문제작처럼 상담이 먼저인 물건이다.
--
-- 그 둘을 같은 화면 상태로 두면 구분이 안 된다. "시세가 끊겼는데 아무도 모르는
-- 상태"와 "원래 문의로만 파는 상품"이 똑같이 보이면, 앞의 장애를 뒤의 정상으로
-- 착각하고 지나치게 된다. 그래서 의도한 시세문의를 별도의 값으로 둔다.
-- =====================================================================

ALTER TABLE products DROP CONSTRAINT products_price_type_check;
ALTER TABLE products ADD CONSTRAINT products_price_type_check
      CHECK (price_type IN ('FIXED', 'MARKET_LINKED', 'MARKET_PLUS',
                            'MARKET_MULTIPLIER', 'MANUAL', 'INQUIRY'));

COMMENT ON COLUMN products.price_type IS
    '가격 산출 방식. MARKET_* = 자동(시세 연동), FIXED/MANUAL = 수동(고정), INQUIRY = 시세문의(값을 내걸지 않음)';

-- ---------- 필수 항목 제약을 다시 잡는다 ----------
--
-- V3 의 chk_price_type_fields 는 두 갈래였다:
--   FIXED/MANUAL          → fixed_price 필수
--   그 외(=시세 연동)     → metal_type, weight_g 필수
--
-- INQUIRY 를 그냥 두면 "그 외"에 묶여 금속과 중량을 강제받는다. 값을 내걸지
-- 않기로 한 상품에 "몇 그램인지 반드시 적어라"라고 요구하는 셈이라, 등록하려면
-- 있지도 않은 중량을 지어내야 한다. 그 순간 이 컬럼의 데이터는 신뢰를 잃는다.
--
-- 세 갈래로 다시 나눈다.
ALTER TABLE products DROP CONSTRAINT chk_price_type_fields;
ALTER TABLE products ADD CONSTRAINT chk_price_type_fields CHECK (
       (price_type IN ('FIXED', 'MANUAL') AND fixed_price IS NOT NULL)
    OR (price_type = 'INQUIRY')
    OR (price_type IN ('MARKET_LINKED', 'MARKET_PLUS', 'MARKET_MULTIPLIER')
        AND metal_type IS NOT NULL AND weight_g IS NOT NULL)
);
