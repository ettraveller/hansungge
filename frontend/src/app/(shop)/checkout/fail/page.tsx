import type { Metadata } from 'next';
import { Suspense } from 'react';

import { PaymentFailView } from '@/views/PaymentResultView';

export const metadata: Metadata = {
  title: '결제 실패',
  robots: { index: false, follow: false },
};

export default function PaymentFailPage() {
  return (
    <Suspense fallback={<p style={{ padding: 60, textAlign: 'center' }}>불러오는 중…</p>}>
      <PaymentFailView />
    </Suspense>
  );
}
