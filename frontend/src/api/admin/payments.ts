import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';

/** 결제 상태 — 백엔드 PaymentStatus 와 같은 값이다. */
export type PaymentStatus =
  | 'READY'
  | 'APPROVED'
  | 'FAILED'
  | 'CANCELLED'
  | 'PARTIAL_REFUNDED'
  | 'REFUNDED';

/**
 * 관리자 결제 내역 한 줄. 주문이 아니라 <b>결제 시도</b>가 단위다 —
 * 한 주문에 시도가 여러 줄일 수 있다 (결제창에서 나갔다가 다시 등).
 */
export interface AdminPayment {
  id: number;
  orderId: number;
  orderNo: string;
  provider: string;
  method: string | null;
  status: PaymentStatus;
  statusLabel: string;
  amount: number;
  pgTid: string | null;
  approvedAt: string | null;
  createdAt: string;
}

export interface AdminPaymentSearch {
  status?: PaymentStatus | null;
  keyword?: string | null;
  page?: number;
  size?: number;
}

export function listPayments(search: AdminPaymentSearch = {}) {
  return adminApi.get<PageResponse<AdminPayment>>('/api/admin/payments', {
    query: {
      status: search.status ?? undefined,
      keyword: search.keyword ?? undefined,
      page: search.page ?? 0,
      size: search.size ?? 20,
    },
  });
}
