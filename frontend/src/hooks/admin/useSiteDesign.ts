'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { adminApi } from '@/api/admin/client';
import { siteDesignApi, type SiteDesign } from '@/api/siteDesign';

const KEY = ['admin', 'site-design'] as const;

/** 화면 꾸미기 설정. 조회는 공개 API 와 같은 것을 쓴다 — 손님 화면이 읽는 그 값이다. */
export function useSiteDesign() {
  return useQuery({ queryKey: KEY, queryFn: siteDesignApi.get, staleTime: 0 });
}

export function useSaveSiteDesign() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (design: SiteDesign) =>
      adminApi.put<SiteDesign>('/api/admin/site-design', { body: design }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
