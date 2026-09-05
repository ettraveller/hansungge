import type { Metadata } from 'next';

import { PrivacyView } from '@/views/legal/PrivacyView';

export const metadata: Metadata = {
  title: '개인정보처리방침',
  description:
    '한성금거래소 개인정보처리방침. 수집하는 개인정보 항목, 이용 목적, 보유 기간, 위탁 현황과 이용자의 권리를 안내합니다.',
};

/**
 * 개인정보처리방침 (`/privacy`).
 *
 * <p>푸터가 이미 이 주소를 가리키고 있었고, 소셜 로그인 3사 검수도 모두 이 URL 을
 * 요구한다 — 구글 프로덕션 전환의 마지막 요구 항목이 이것이다.
 */
export default function PrivacyPage() {
  return <PrivacyView />;
}
