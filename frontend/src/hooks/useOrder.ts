'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as orderApi from '@/api/order';
import { cartKeys } from '@/hooks/useCart';
import type { OrderCreateInput, OrderListQuery } from '@/types/order';

/**
 * 주문·결제 훅.
 *
 * <p>주문이 만들어지면 장바구니가 비므로 장바구니 캐시를 함께 무효화한다 — 그러지 않으면
 * 헤더 배지에 방금 주문한 상품 수가 계속 남는다.
 */

export const orderKeys = {
  all: ['orders'] as const,
  list: (query: OrderListQuery) => [...orderKeys.all, 'list', query] as const,
  detail: (orderNo: string) => [...orderKeys.all, 'detail', orderNo] as const,
  shipment: (orderNo: string) => [...orderKeys.all, 'shipment', orderNo] as const,
  paymentConfig: ['payment-config'] as const,
};

export function useOrders(query: OrderListQuery = {}) {
  return useQuery({
    queryKey: orderKeys.list(query),
    queryFn: () => orderApi.fetchOrders(query),
  });
}

export function useOrder(orderNo: string | null) {
  return useQuery({
    queryKey: orderKeys.detail(orderNo ?? ''),
    queryFn: () => orderApi.fetchOrder(orderNo as string),
    enabled: !!orderNo,
  });
}

/**
 * 배송 정보.
 *
 * <p>캐시에 묶어 두지 않는다. 관리자가 송장을 넣은 직후 고객이 새로고침하면 바로
 * 보여야 한다 — "발송했다는 문자는 받았는데 화면에는 없다"가 문의로 돌아온다.
 *
 * <p>결제 전 주문에는 부르지 않는다({@code enabled}). 아직 보낼 것이 없는 주문에
 * 배송칸을 띄우면 "준비 중"이 아니라 "뭔가 빠진 화면"으로 읽힌다.
 */
export function useShipment(orderNo: string | null, enabled = true) {
  return useQuery({
    queryKey: orderKeys.shipment(orderNo ?? ''),
    queryFn: () => orderApi.fetchShipment(orderNo as string),
    enabled: !!orderNo && enabled,
    staleTime: 0,
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
    // 캐시에 묶어 두지 않는다. 서버에서 결제 키를 넣고 재시작해도 열어 둔 탭이
    // 옛 답(available=false)을 계속 들고 있어, "설정했는데 여전히 막혀 있다"가 된다.
    // 설정 한 줄 읽는 요청이라 매번 물어도 부담이 없다.
    staleTime: 0,
    refetchOnMount: true,
    retry: false,
  });
}

export function useConfirmPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { orderNo: string; paymentKey: string }) =>
      orderApi.confirmPayment(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
      // 결제가 끝나면 서버가 그 상품을 장바구니에서 지운다. 여기서 캐시를 비우지
      // 않으면 헤더 배지에 방금 산 상품 수가 남는다 — 눌러 보면 비어 있어서
      // "장바구니가 깨졌나" 로 보인다.
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
}
