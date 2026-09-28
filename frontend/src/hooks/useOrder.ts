'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as orderApi from '@/api/order';
import { cartKeys } from '@/hooks/useCart';
import type { OrderCreateInput } from '@/types/order';

/**
 * 주문·결제 훅.
 *
 * <p>주문이 만들어지면 장바구니가 비므로 장바구니 캐시를 함께 무효화한다 — 그러지 않으면
 * 헤더 배지에 방금 주문한 상품 수가 계속 남는다.
 */

export const orderKeys = {
  all: ['orders'] as const,
  list: (page: number) => [...orderKeys.all, 'list', page] as const,
  detail: (orderNo: string) => [...orderKeys.all, 'detail', orderNo] as const,
  paymentConfig: ['payment-config'] as const,
};

export function useOrders(page = 0, size = 10) {
  return useQuery({
    queryKey: orderKeys.list(page),
    queryFn: () => orderApi.fetchOrders(page, size),
  });
}

export function useOrder(orderNo: string | null) {
  return useQuery({
    queryKey: orderKeys.detail(orderNo ?? ''),
    queryFn: () => orderApi.fetchOrder(orderNo as string),
    enabled: !!orderNo,
  });
}

export function useCreateOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: OrderCreateInput) => orderApi.createOrder(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderNo: string) => orderApi.cancelOrder(orderNo),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: orderKeys.all }),
  });
}

/**
 * 결제 설정.
 *
 * <p>키는 배포 중에 바뀌는 값이 아니라 자주 다시 묻지 않는다. 대신 설정이 없을 때
 * 화면이 결제 버튼을 그리지 않아야 하므로, 실패해도 조용히 available=false 로 둔다.
 */
export function usePaymentConfig() {
  return useQuery({
    queryKey: orderKeys.paymentConfig,
    queryFn: () => orderApi.fetchPaymentConfig(),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

export function useConfirmPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { orderNo: string; paymentKey: string }) =>
      orderApi.confirmPayment(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: orderKeys.all }),
  });
}
