import type { Metadata } from 'next';

import { AdminOrderDetailView } from '@/views/admin/AdminOrderDetailView';

export const metadata: Metadata = {
  title: '주문 상세',
  robots: { index: false, follow: false },
};

/**
 * 주문 상세 (`/admin/orders/{id}`).
 *
 * <p>id 가 숫자가 아니면 NaN 이 그대로 API 로 가서 500 이 난다. 화면에서 걸러
 * "주문을 불러오지 못했습니다"로 끝나게 한다.
 */
export default async function AdminOrderDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <AdminOrderDetailView orderId={Number(id)} />;
}
