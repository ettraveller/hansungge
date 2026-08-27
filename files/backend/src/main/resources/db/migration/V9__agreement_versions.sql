-- =====================================================================
-- V9 — 약관 버전 설정 (STEP 9, 개발계획 §7.3)
--
-- 동의 이력({@code user_agreements})은 "무엇에 동의했는가"를 특정하기 위해 그 시점의
-- 약관 버전을 함께 남긴다. 그 버전을 코드 상수로 두면 문구를 고칠 때마다 배포가
-- 필요하고, 배포 전까지는 틀린 버전이 기록된다. 약관 개정은 법무·운영이 하는 일이라
-- 개발 배포 주기와 무관해야 한다.
--
-- 값이 없어도 애플리케이션은 '1.0' 으로 동작한다(AgreementCatalog.FALLBACK_VERSION).
-- 여기서 행을 만들어 두는 이유는 관리자 화면에서 보이고 고칠 수 있게 하기 위해서다.
-- =====================================================================

INSERT INTO system_settings(key, value, description) VALUES
 ('AGREEMENT_TERMS_VERSION',       '1.0', '이용약관 버전. 개정 시 올린다'),
 ('AGREEMENT_PRIVACY_VERSION',     '1.0', '개인정보 수집·이용 동의 버전'),
 ('AGREEMENT_AGE_14_VERSION',      '1.0', '만 14세 이상 확인 문구 버전'),
 ('AGREEMENT_MARKETING_VERSION',   '1.0', '마케팅 수신 동의 버전 (선택)'),
 ('AGREEMENT_THIRD_PARTY_VERSION', '1.0', '개인정보 제3자 제공 동의 버전 (선택)')
ON CONFLICT (key) DO NOTHING;
