import type { Metadata } from 'next';

import { RequireMember } from '@/components/auth/RequireMember';
import { PaymentView } from '@/views/PaymentView';

export const metadata: Metadata = {
  title: '결제',
  robots: { index: false, follow: false },
};

export default async function PaymentPage({ params }: { params: Promise<{ orderNo: string }> }) {
  const { orderNo } = await params;
  // 바깥 여백은 페이지에서 준다 — 이유는 checkout/page.tsx 참조.
  return (
    <div className="wrap">
      <RequireMember><PaymentView orderNo={decodeURIComponent(orderNo)} /></RequireMember>
    </div>
  );
}
