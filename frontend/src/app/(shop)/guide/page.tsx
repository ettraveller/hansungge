import type { Metadata } from 'next';

import { api } from '@/api/client';
import { load } from '@/api/server';
import { GuideView } from '@/views/legal/GuideView';
import type { PaymentConfig } from '@/types/order';

export const metadata: Metadata = {
  title: '이용안내',
  description:
    '한성금거래소 주문 전 확인사항, 배송, 교환·반품·환불, A/S 안내. 시세 연동 현물 상품의 거래 기준을 안내합니다.',
};

/**
 * 무통장입금 기한은 관리자가 정한다 (관리자 › 입금계좌). 글에 숫자를 박아 두지 않고
 * 그릴 때마다 읽는다 — 관리자가 바꾸면 이 안내와 주문서가 같이 바뀐다.
 */
export const dynamic = 'force-dynamic';

export default async function GuidePage() {
  const config = await load(
    () => api.get<PaymentConfig>('/api/v1/payments/config', { cache: 'no-store' }),
    '이용안내 입금 기한',
  );
  return <GuideView depositDueHours={config.data?.depositDueHours ?? null} />;
}
