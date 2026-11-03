'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as ordersApi from '@/api/admin/orders';
import { adminKeys } from './keys';
import type { AdminOrderSearch, Carrier } from '@/types/order-admin';

export function useAdminOrders(search: AdminOrderSearch) {
  return useQuery({
    queryKey: adminKeys.orderList(search),
    queryFn: () => ordersApi.listOrders(search),
    // 주문은 지금 들어오는 중이다. 목록이 한 박자 늦으면 방금 결제한 주문이 안 보인다.
    staleTime: 0,
  });
}

export function useAwaitingCount() {
  return useQuery({
    queryKey: adminKeys.orderAwaitingCount(),
    queryFn: () => ordersApi.awaitingCount(),
    staleTime: 0,
  });
}

export function useAdminOrder(id: number | null) {
  return useQuery({
    queryKey: adminKeys.order(id ?? 0),
    queryFn: () => ordersApi.getOrder(id as number),
    enabled: id != null,
    staleTime: 0,
  });
}

export function useShipment(id: number | null) {
  return useQuery({
    queryKey: adminKeys.orderShipment(id ?? 0),
    queryFn: () => ordersApi.getShipment(id as number),
    enabled: id != null,
    staleTime: 0,
  });
}

/**
 * 배송을 바꾸는 동작들.
 *
 * <p>성공하면 <b>주문 관련 캐시를 통째로</b> 버린다. 송장을 넣으면 배송만 바뀌는 것이
 * 아니라 주문 상태도 바뀌고, 그러면 목록의 줄과 상단의 "발송 대기 N건"도 함께 낡는다.
 * 하나씩 집어 무효화하면 한 군데를 빠뜨리는 날 화면끼리 다른 말을 한다.
 */
function useOrderMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.orders() }),
  });
}

export function useMarkPreparing() {
  return useOrderMutation((id: number) => ordersApi.markPreparing(id));
}

export function useConfirmDeposit() {
  return useOrderMutation((id: number) => ordersApi.confirmDeposit(id));
}

export function useShip() {
  return useOrderMutation(
    ({ id, trackingNo, carrier }: { id: number; trackingNo: string; carrier?: Carrier }) =>
      ordersApi.ship(id, trackingNo, carrier),
  );
}

export function useMarkDelivered() {
  return useOrderMutation((id: number) => ordersApi.markDelivered(id));
}
