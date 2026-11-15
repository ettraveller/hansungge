'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { adminApi } from '@/api/admin/client';
import { footerApi, type FooterInfo } from '@/api/footer';

const KEY = ['admin', 'footer'] as const;

/**
 * 지금 푸터에 나가는 값.
 *
 * <p>공개 조회를 그대로 쓴다 — 관리자용 조회를 따로 두면 같은 값을 주는 길이 두
 * 벌이 되고, 칸을 더하는 날 한쪽만 고쳐진다. 담긴 것은 어차피 손님 화면에 그대로
 * 나가는 공개 정보다.
 */
export function useFooterInfo() {
  return useQuery({ queryKey: KEY, queryFn: footerApi.get, staleTime: 0 });
}

export function useSaveFooter() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: FooterInfo) =>
      adminApi.put<FooterInfo>('/api/admin/settings/footer', { body }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
