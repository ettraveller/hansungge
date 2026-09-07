'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';

import * as membersApi from '@/api/admin/members';
import type { MemberSearch, MemberStatus } from '@/types/member';
import { adminKeys } from './keys';

export function useMembers(search: MemberSearch) {
  return useQuery({
    queryKey: adminKeys.memberList(search),
    queryFn: () => membersApi.listMembers(search),
  });
}

export function useMemberCounts() {
  return useQuery({
    queryKey: adminKeys.memberCounts(),
    queryFn: membersApi.memberCounts,
    staleTime: 30_000,
  });
}

/**
 * 회원 상세.
 *
 * <p>{@code enabled} 로 열었을 때만 부른다. 이 조회는 <b>감사로그에 남으므로</b>,
 * 목록을 그리면서 미리 받아 두면 열어보지도 않은 회원의 열람 기록이 쌓인다.
 *
 * <p>같은 이유로 캐시를 오래 들고 있지 않는다 — 창을 닫았다 다시 열면 다시 기록되는
 * 편이, 기록 없이 보이는 것보다 낫다.
 */
export function useMemberDetail(id: number | null) {
  return useQuery({
    queryKey: adminKeys.member(id ?? 0),
    queryFn: () => membersApi.memberDetail(id as number),
    enabled: id != null,
    staleTime: 0,
    gcTime: 0,
  });
}

export function useChangeMemberStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, reason }: { id: number; status: MemberStatus; reason: string }) =>
      membersApi.changeMemberStatus(id, status, reason),
    onSuccess: () => invalidateMembers(queryClient),
  });
}

export function useUnlockMember() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      membersApi.unlockMember(id, reason),
    onSuccess: () => invalidateMembers(queryClient),
  });
}

/** 목록·요약·상세가 함께 바뀐다. 하나만 지우면 화면 어딘가가 옛 값으로 남는다. */
function invalidateMembers(queryClient: QueryClient) {
  return queryClient.invalidateQueries({ queryKey: adminKeys.members() });
}
