'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as bannersApi from '@/api/admin/banners';

const KEY = ['admin', 'banners'] as const;

export function useAdminBanners() {
  return useQuery({ queryKey: KEY, queryFn: bannersApi.listBanners, staleTime: 0 });
}

export function useReplaceBanners() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: bannersApi.replaceBanners,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}
