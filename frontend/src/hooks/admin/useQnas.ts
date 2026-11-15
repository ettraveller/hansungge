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

/**
 * 게시판 탭별 미답변 건수 — 문의 관리 화면의 탭 배지.
 *
 * <p>총건수(위)와 달리 staleTime 0 이다. 배지를 보며 답변하는 화면이라,
 * 답을 달고 돌아왔는데 숫자가 30초 전 것이면 한 번 더 누르게 된다.
 */
export function useUnansweredQnaCounts() {
  return useQuery({
    queryKey: adminKeys.qnaUnansweredCounts(),
    queryFn: qnasApi.unansweredCounts,
    staleTime: 0,
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
