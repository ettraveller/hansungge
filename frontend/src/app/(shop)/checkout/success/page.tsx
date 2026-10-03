import type { Metadata } from 'next';
import { Suspense } from 'react';

import { RequireMember } from '@/components/auth/RequireMember';
import { PageHead } from '@/components/ui/PageHead';
import { PaymentSuccessView } from '@/views/PaymentResultView';

export const metadata: Metadata = {
  title: '결제 완료',
  robots: { index: false, follow: false },
};

/**
 * 토스가 돌려보내는 자리.
 *
 * useSearchParams 를 쓰는 화면은 Suspense 로 감싸야 한다 — 없으면 빌드가
 * "useSearchParams should be wrapped in a suspense boundary" 로 멈춘다.
 */
export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={<p style={{ padding: 60, textAlign: 'center' }}>결제를 확인하고 있습니다…</p>}>
      {/* 바깥 여백은 페이지에서 준다 — 이유는 checkout/page.tsx 참조. */}
      <div className="wrap">
        <PageHead title="결제 완료" />
        <RequireMember><PaymentSuccessView /></RequireMember>
      </div>
    </Suspense>
  );
}
