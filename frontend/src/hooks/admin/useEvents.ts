'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as eventsApi from '@/api/admin/events';
import type { AdminEventSearch, EventWriteRequest } from '@/api/admin/events';
import { adminKeys } from './keys';

export function useAdminEvents(search: AdminEventSearch) {
  return useQuery({
    queryKey: adminKeys.eventList(search),
    queryFn: () => eventsApi.listEvents(search),
  });
}

/**
 * 응모 목록.
 *
 * <p>{@code enabled} 로 막는 이유 — 이 훅은 응모자 모달 안에서만 쓰이는데, 훅은
 * 조건부로 호출할 수 없다. 모달이 닫힌 상태에서 eventId 가 null 이면 요청을 쉰다.
 */
export function useEventEntries(eventId: number | null, page: number) {
  return useQuery({
    queryKey: adminKeys.eventEntries(eventId ?? -1, page),
    queryFn: () => eventsApi.listEntries(eventId as number, page),
    enabled: eventId != null,
  });
}

export function useCreateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: EventWriteRequest) => eventsApi.createEvent(body),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useUpdateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: EventWriteRequest }) =>
      eventsApi.updateEvent(id, body),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useHideEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => eventsApi.hideEvent(id),
    onSuccess: () => invalidate(queryClient),
  });
}

/**
 * 응모 목록까지 함께 무효화한다.
 *
 * <p>기간을 바꾸면 서버가 내려주는 status(진행예정/진행중/종료)가 달라지고, 종료로
 * 바뀐 이벤트는 더 이상 응모를 받지 않는다. 목록만 갱신하면 두 값이 어긋난다.
 */
function invalidate(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: adminKeys.events() });
}
