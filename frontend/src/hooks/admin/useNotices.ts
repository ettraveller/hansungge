'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as noticesApi from '@/api/admin/notices';
import type { AdminNoticeSearch, NoticeWriteRequest } from '@/api/admin/notices';
import { adminKeys } from './keys';

export function useAdminNotices(search: AdminNoticeSearch) {
  return useQuery({
    queryKey: adminKeys.noticeList(search),
    queryFn: () => noticesApi.listNotices(search),
  });
}

export function useCreateNotice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: NoticeWriteRequest) => noticesApi.createNotice(body),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useUpdateNotice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: NoticeWriteRequest }) =>
      noticesApi.updateNotice(id, body),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useHideNotice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => noticesApi.hideNotice(id),
    onSuccess: () => invalidate(queryClient),
  });
}

/**
 * 페이지 번호까지 키에 들어 있어 목록 전체를 무효화한다.
 *
 * <p>고정(pinned)을 켜면 그 공지가 1페이지 맨 위로 올라간다 — 지금 보고 있는 페이지만
 * 새로 받으면 방금 고정한 공지가 화면에서 사라진 것처럼 보인다.
 */
function invalidate(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: adminKeys.notices() });
}
