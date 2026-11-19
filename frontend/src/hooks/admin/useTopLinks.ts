'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { adminApi } from '@/api/admin/client';
import { topLinksApi, type TopLink } from '@/api/topLinks';

const KEY = ['admin', 'top-links'] as const;

/**
 * 지금 띠에 걸린 링크.
 *
 * <p>공개 조회를 그대로 쓴다 — 꺼 둔 링크라는 것이 없어서 손님이 보는 목록과 고칠
 * 목록이 같다. 관리자용 조회를 따로 두면 같은 값을 주는 길이 두 벌이 된다.
 */
export function useTopLinks() {
  return useQuery({ queryKey: KEY, queryFn: topLinksApi.get, staleTime: 0 });
}

/** 보낸 것이 곧 전체이고, 보낸 차례가 띠에 나가는 차례다. */
export function useReplaceTopLinks() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (links: Pick<TopLink, 'label' | 'href' | 'iconKey'>[]) =>
      adminApi.put<TopLink[]>('/api/admin/top-links', { body: { links } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
