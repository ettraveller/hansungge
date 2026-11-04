'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as popupsApi from '@/api/admin/popups';
import type { PopupInput } from '@/types/popup';
import { adminKeys } from './keys';

export function useAdminPopups() {
  return useQuery({
    queryKey: adminKeys.popups(),
    queryFn: popupsApi.listPopups,
  });
}

/**
 * 팝업을 바꾸는 동작들.
 *
 * <p>성공하면 목록을 통째로 다시 읽는다. 「지금 떠 있는가」는 켬/끔과 기간을 서버가
 * 합쳐 판정한 값이라, 한 줄만 손으로 고쳐 넣으면 그 값이 낡은 채로 남는다.
 */
function usePopupMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.popups() }),
  });
}

export function useCreatePopup() {
  return usePopupMutation((body: PopupInput) => popupsApi.createPopup(body));
}

export function useUpdatePopup() {
  return usePopupMutation(({ id, body }: { id: number; body: PopupInput }) =>
    popupsApi.updatePopup(id, body),
  );
}

export function useSetPopupActive() {
  return usePopupMutation(({ id, active }: { id: number; active: boolean }) =>
    popupsApi.setPopupActive(id, active),
  );
}

export function useDeletePopup() {
  return usePopupMutation((id: number) => popupsApi.deletePopup(id));
}
