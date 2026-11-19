import type { Metadata } from 'next';

import { AdminPaymentsView } from '@/views/admin/AdminPaymentsView';

export const metadata: Metadata = {
  title: '결제관리',
  robots: { index: false, follow: false },
};

/**
 * 결제관리 (`/admin/payments`).
 *
 * <p>2026-11-17 에 만들었다. 토스 라이브 전환과 함께 — 돈이 실제로 오가기 시작하면
 * 주문 상태와 별개로 결제 기록을 봐야 하는 날이 온다 (승인은 됐는데 주문이 미결제로
 * 남는 사고를 찾는 화면이다).
 */
export default function AdminPaymentsPage() {
  return <AdminPaymentsView />;
}
