'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as policiesApi from '@/api/admin/policies';
import { adminKeys } from './keys';

export function useRefundPolicy() {
  return useQuery({
    queryKey: adminKeys.refundPolicy(),
    queryFn: policiesApi.getRefundPolicy,
  });
}

export function useRefundPolicyHistory(page: number) {
  return useQuery({
    queryKey: adminKeys.refundPolicyHistory(page),
    queryFn: () => policiesApi.getRefundPolicyHistory(page),
  });
}

export function usePublishRefundPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ content, changeNote }: { content: string; changeNote: string }) =>
      policiesApi.publishRefundPolicy(content, changeNote),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.policies() });
      // 발행은 감사로그를 남긴다.
      void queryClient.invalidateQueries({ queryKey: [...adminKeys.all, 'audit-logs'] });
    },
  });
}
