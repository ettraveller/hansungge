import type { Metadata } from 'next';

import { TermsView } from '@/views/legal/TermsView';

export const metadata: Metadata = {
  title: '이용약관',
  description:
    '한성금거래소 이용약관. 회원가입과 계약 성립, 고시 시세에 따른 판매가격 산정, 대금 지급, 배송, 청약철회와 분쟁 해결에 관한 사항을 정합니다.',
};

/**
 * 이용약관 (`/terms`).
 *
 * <p>푸터가 이 주소를 링크하고, 전자상거래법 §13 상 표시 의무이며, 소셜 로그인 3사
 * 검수와 결제대행사 심사가 모두 이 URL 을 확인한다.
 *
 * <p>2026-10-30 까지는 "준비 중" 안내만 있었다. 거래 조건이 정해지지 않은 상태에서
 * 내용을 지어내면 그대로 효력을 갖는 약속이 되기 때문이었는데, 이제 가격 산정·주문·
 * 배송·청약철회가 모두 확정되어 사실대로 쓸 수 있다.
 *
 * <p>⚠️ robots 를 열었다. 준비 중일 때는 색인을 막아 두었다.
 */
export default function TermsPage() {
  return <TermsView />;
}
