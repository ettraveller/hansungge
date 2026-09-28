import type { Metadata } from 'next';

import { RequireMember } from '@/components/auth/RequireMember';
import { PaymentView } from '@/views/PaymentView';

export const metadata: Metadata = {
  title: '결제',
  robots: { index: false, follow: false },
};

export default async function PaymentPage({ params }: { params: Promise<{ orderNo: string }> }) {
  const { orderNo } = await params;
  return <RequireMember><PaymentView orderNo={decodeURIComponent(orderNo)} /></RequireMember>;
}
