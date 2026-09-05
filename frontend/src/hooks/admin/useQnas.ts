'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as qnasApi from '@/api/admin/qnas';
import type { AdminQnaSearch } from '@/api/admin/qnas';
import { adminKeys } from './keys';

export function useAdminQnas(search: AdminQnaSearch) {
  return useQuery({
    queryKey: adminKeys.qnaList(search),
    queryFn: () => qnasApi.listQnas(search),
  });
}

/**
 * 미답변 건수.
 *
 * <p>답변이 늦는 것이 이 게시판의 유일한 실패 방식이라, 숫자를 대시보드까지 올린다.
 * 목록을 열어야만 알 수 있으면 아무도 열지 않는 날 밀린다.
 */
export function useUnansweredQnaCount() {
  return useQuery({
    queryKey: adminKeys.qnaUnansweredCount(),
    queryFn: qnasApi.unansweredCount,
    staleTime: 30_000,
  });
}

export function useAnswerQna() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, answer }: { id: number; answer: string }) => qnasApi.answerQna(id, answer),
    onSuccess: () => invalidateQnas(queryClient),
  });
}

export function useHideQna() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => qnasApi.hideQna(id),
    onSuccess: () => invalidateQnas(queryClient),
  });
}

/**
 * 목록과 미답변 배지를 함께 무효화한다.
 *
 * <p>답변한 뒤 배지 숫자가 그대로면 "답변이 안 들어갔나" 하고 한 번 더 누른다.
 */
function invalidateQnas(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: adminKeys.qnas() });
}
