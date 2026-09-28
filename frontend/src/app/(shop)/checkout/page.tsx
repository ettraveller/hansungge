import type { Metadata } from 'next';

import { RequireMember } from '@/components/auth/RequireMember';
import { CheckoutView } from '@/views/CheckoutView';

export const metadata: Metadata = {
  title: '주문서 작성',
  robots: { index: false, follow: false },
};

export default function CheckoutPage() {
  return <RequireMember><CheckoutView /></RequireMember>;
}
