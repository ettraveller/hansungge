'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as addressApi from '@/api/address';
import type { DeliveryAddressInput } from '@/types/order';

export const addressKeys = {
  all: ['addresses'] as const,
};

/**
 * 저장해 둔 배송지.
 *
 * <p>{@code enabled} 로 로그인 전 호출을 막는다 — 비회원 화면에서 부르면 401 이 돌아오고,
 * 그 401 이 토큰 갱신 흐름을 쓸데없이 깨운다.
 */
export function useAddresses(enabled = true) {
  return useQuery({
    queryKey: addressKeys.all,
    queryFn: addressApi.fetchAddresses,
    enabled,
  });
}

function useAddressMutation<T>(fn: (input: T) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: addressKeys.all }),
  });
}

export function useCreateAddress() {
  return useAddressMutation((input: DeliveryAddressInput) => addressApi.createAddress(input));
}

export function useUpdateAddress() {
  return useAddressMutation(({ id, input }: { id: number; input: DeliveryAddressInput }) =>
    addressApi.updateAddress(id, input));
}

export function useDeleteAddress() {
  return useAddressMutation((id: number) => addressApi.deleteAddress(id));
}

export function useMakeDefaultAddress() {
  return useAddressMutation((id: number) => addressApi.makeDefaultAddress(id));
}
