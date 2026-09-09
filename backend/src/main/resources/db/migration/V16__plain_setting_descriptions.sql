-- =====================================================================
-- V16 — 설정 설명을 사람 말로 바꾼다
--
-- 이 설명은 관리자 화면에 그대로 노출된다. 그런데 "시세 노후 판정(분)"처럼
-- 적혀 있으면 읽는 사람이 무슨 일이 벌어지는지 알 수 없다. 실제로 물어보게
-- 되는 것은 "이게 뭔데 이렇게 해뒀냐"이고, 그 질문이 나오는 설명은 실패한
-- 설명이다.
--
-- 값은 건드리지 않는다. 바꾸는 것은 말뿐이다.
-- =====================================================================

UPDATE system_settings
   SET description = '마지막 고시로부터 이 시간(분)이 지나면 금액을 감추고 "시세문의"로 '
                     || '바꾼다. 오래된 시세로 팔면 손해가 나기 때문이다. 240 = 4시간. '
                     || '자동고시를 켜 두면 10분마다 갱신되어 여기 걸릴 일이 없다.',
       updated_at = now()
 WHERE key = 'PRICE_STALE_MINUTES';

UPDATE system_settings
   SET description = '시세를 자동으로 받아오는 주기. 비워 두면 자동 수집이 돌지 않는다.',
       updated_at = now()
 WHERE key = 'MARKET_FETCH_CRON';

UPDATE system_settings
   SET description = '한 번에 이 비율(%)을 넘게 시세가 움직이면 바로 반영하지 않고 '
                     || '관리자 승인을 기다린다. 잘못된 값이 그대로 판매가가 되는 것을 막는다.',
       updated_at = now()
 WHERE key = 'MAX_PRICE_CHANGE_PERCENT';

UPDATE system_settings
   SET description = '최종 판매가를 어느 단위로 맞출지. CEIL_100 = 백원 단위 올림.',
       updated_at = now()
 WHERE key = 'PRICE_ROUNDING_MODE';

UPDATE system_settings
   SET description = '부가세를 표시가에 어떻게 반영할지. '
                     || 'NONE = 반영 안 함 / INCLUDED = 포함 / EXCLUDED = 별도 부과.',
       updated_at = now()
 WHERE key = 'VAT_MODE';
