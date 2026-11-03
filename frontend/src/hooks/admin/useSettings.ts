'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as settingsApi from '@/api/admin/settings';
import { adminKeys } from './keys';

export function useBankAccount() {
  return useQuery({
    queryKey: adminKeys.bankAccount(),
    queryFn: settingsApi.getBankAccount,
  });
}

export function useSaveBankAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: settingsApi.saveBankAccount,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.settings() });
      // 계좌를 비우면 고객 쪽 무통장 선택지가 사라진다. 그 응답(payment-config)은
      // 쇼핑몰 캐시라 관리자 캐시를 비워도 안 바뀐다 — 다른 브라우저 탭 이야기다.
      // 여기서 할 수 있는 일은 없고, 고객 화면은 다음 조회에서 알게 된다.
    },
  });
}
