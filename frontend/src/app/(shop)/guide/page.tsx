import type { Metadata } from 'next';

import { GuideView } from '@/views/legal/GuideView';

export const metadata: Metadata = {
  title: '이용안내',
  description:
    '한성금거래소 주문 전 확인사항, 배송, 교환·반품·환불, A/S 안내. 시세 연동 현물 상품의 거래 기준을 안내합니다.',
};

export default function GuidePage() {
  return <GuideView />;
}
