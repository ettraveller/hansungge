import { notFound } from 'next/navigation';

import { AdminProductDetailView } from '@/views/admin/AdminProductDetailView';

export const metadata = { title: '상품 상세' };

/** Next.js 16 에서 params 는 Promise 다 — 반드시 await 한다. */
export default async function AdminProductDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const productId = Number(id);
  if (!Number.isInteger(productId) || productId <= 0) notFound();

  return <AdminProductDetailView productId={productId} />;
}
