'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { adminApi } from '@/api/admin/client';
import type { Faq } from '@/api/faqs';

const KEY = ['admin', 'faqs'] as const;

/** 관리자용 목록 — 꺼 둔 것까지 온다. */
export function useAdminFaqs() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => adminApi.get<Faq[]>('/api/admin/faqs'),
    staleTime: 0,
  });
}

/** 통째로 갈아 끼운다. 보낸 것이 곧 전체다. */
export function useReplaceFaqs() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (faqs: Omit<Faq, 'id'>[]) =>
      adminApi.put<Faq[]>('/api/admin/faqs', { body: { faqs } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
