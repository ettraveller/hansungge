'use client';

import { useQuery } from '@tanstack/react-query';

import * as policiesApi from '@/api/policies';

export const policyKeys = {
  all: ['policy'] as const,
  refund: () => [...policyKeys.all, 'refund'] as const,
};

/**
 * 환불규정 현재 판.
 *
 * <p>개정은 드물어 캐시해도 되지만, 주문 생성이 "동의한 판 = 지금 판"을 검사하므로
 * 낡아도 틀어지지 않는다 — 개정 직후라면 서버가 409 를 주고 화면이 다시 받아온다.
 */
export function useRefundPolicy() {
  return useQuery({
    queryKey: policyKeys.refund(),
    queryFn: policiesApi.fetchRefundPolicy,
    staleTime: 5 * 60 * 1000,
  });
}
