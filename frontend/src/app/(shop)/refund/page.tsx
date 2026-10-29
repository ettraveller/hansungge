import type { Metadata } from 'next';

import { RefundView } from '@/views/legal/RefundView';

export const metadata: Metadata = {
  title: '교환 · 반품 · 환불 운영규정',
  description:
    '한성금거래소 교환·반품·환불 운영규정. 청약철회 기간과 제한 사유, 반품 상품 검수 절차, 반품 배송비 부담과 환불 처리 기한을 안내합니다.',
};

/**
 * 교환·반품·환불 운영규정 (`/refund`).
 *
 * <p>결제대행사 심사가 요구하는 「환불정책 URL」이 이 주소다. 이용안내(`/guide`)에
 * 섞어 두면 심사자가 안내 문장 사이에서 규정을 찾아야 한다.
 */
export default function RefundPage() {
  return <RefundView />;
}
