import type { Metadata } from 'next';
import { Suspense } from 'react';

import { OrderListView } from '@/views/OrderListView';

export const metadata: Metadata = {
  title: '주문 내역',
  robots: { index: false, follow: false },
};

/** useSearchParams(group — 요약 줄에서 누른 상태)를 쓰므로 Suspense 로 감싼다 (Next.js 16). */
export default function OrdersPage() {
  return (
    <Suspense fallback={null}>
      <OrderListView />
    </Suspense>
  );
}
