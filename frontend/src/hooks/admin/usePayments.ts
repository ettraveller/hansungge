'use client';

import { useQuery } from '@tanstack/react-query';

import * as paymentsApi from '@/api/admin/payments';
import type { AdminPaymentSearch } from '@/api/admin/payments';
import { adminKeys } from './keys';

export function useAdminPayments(search: AdminPaymentSearch) {
  return useQuery({
    queryKey: adminKeys.paymentList(search),
    queryFn: () => paymentsApi.listPayments(search),
    // 결제는 지금 들어오는 중이다. 목록이 한 박자 늦으면 방금 승인된 결제가 안 보인다.
    staleTime: 0,
  });
}
