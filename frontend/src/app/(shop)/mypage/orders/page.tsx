import type { Metadata } from 'next';

import { OrderListView } from '@/views/OrderListView';

export const metadata: Metadata = {
  title: '주문 내역',
  robots: { index: false, follow: false },
};

export default function OrdersPage() {
  return <OrderListView />;
}
