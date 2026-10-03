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
      {/* 바깥 여백은 페이지에서 준다 — 이유는 checkout/page.tsx 참조. */}
      <div className="wrap">
        <PaymentFailView />
      </div>
    </Suspense>
  );
}
