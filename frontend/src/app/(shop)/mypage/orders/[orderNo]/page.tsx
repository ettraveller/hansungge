import type { Metadata } from 'next';

import { RequireMember } from '@/components/auth/RequireMember';
import { OrderDetailView } from '@/views/OrderDetailView';

export const metadata: Metadata = {
  title: '주문 상세',
  robots: { index: false, follow: false },
};

export default async function OrderDetailPage({ params }: { params: Promise<{ orderNo: string }> }) {
  const { orderNo } = await params;
  return <RequireMember><OrderDetailView orderNo={decodeURIComponent(orderNo)} /></RequireMember>;
}
