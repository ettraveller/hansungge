import type { Metadata } from 'next';

import { RequireMember } from '@/components/auth/RequireMember';
import { PageHead } from '@/components/ui/PageHead';
import { OrderDetailView } from '@/views/OrderDetailView';

export const metadata: Metadata = {
  title: '주문 상세',
  robots: { index: false, follow: false },
};

export default async function OrderDetailPage({ params }: { params: Promise<{ orderNo: string }> }) {
  const { orderNo } = await params;
  // 바깥 여백은 페이지에서 준다 — 이유는 checkout/page.tsx 참조.
  return (
    <div className="wrap">
      <PageHead title="주문 상세" />
      <RequireMember><OrderDetailView orderNo={decodeURIComponent(orderNo)} /></RequireMember>
    </div>
  );
}
