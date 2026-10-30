import type { Metadata } from 'next';

import { AdminOrdersView } from '@/views/admin/AdminOrdersView';

export const metadata: Metadata = {
  title: '주문관리',
  robots: { index: false, follow: false },
};

/**
 * 주문관리 (`/admin/orders`).
 *
 * <p>2026-10-30 에 만들었다. 그전까지는 주문이 들어와도 관리자가 볼 화면이 없었다 —
 * 받는 분·주소·품목을 확인할 길이 없고, 결제가 끝나면 상태가 PAID 에서 멈췄다.
 */
export default function AdminOrdersPage() {
  return <AdminOrdersView />;
}
