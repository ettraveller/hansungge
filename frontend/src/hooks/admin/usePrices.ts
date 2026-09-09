'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as pricesApi from '@/api/admin/prices';
import { adminKeys } from './keys';
import type { PendingStatus } from '@/types/admin';
import type { MetalType, PriceSide } from '@/types/price';

/**
 * 시세는 관리자 화면에서 가장 자주 바뀌는 값이다. 다만 자동 폴링은 두지 않는다 —
 * 관리자가 보고 있는 숫자가 조작 중에 바뀌면 잘못된 값을 승인할 수 있다.
 * 갱신은 고시·승인 후 명시적 무효화로만 일어난다.
 */
export function useCurrentPrices() {
  return useQuery({
    queryKey: adminKeys.currentPrices(),
    queryFn: pricesApi.currentPrices,
    staleTime: 10_000,
  });
}

export function useCollectionStatus() {
  return useQuery({
    queryKey: adminKeys.collectionStatus(),
    queryFn: pricesApi.collectionStatus,
    staleTime: 30_000,
  });
}

export function usePolicies() {
  return useQuery({ queryKey: adminKeys.policies(), queryFn: pricesApi.policies });
}

export function usePriceHistory(metal: MetalType, side: PriceSide, page: number) {
  return useQuery({
    queryKey: adminKeys.priceHistory(metal, side, page),
    queryFn: () => pricesApi.priceHistory(metal, side, page),
  });
}

export function usePendingPrices(status: PendingStatus, page: number) {
  return useQuery({
    queryKey: adminKeys.pending(status, page),
    queryFn: () => pricesApi.pendingPrices(status, page),
  });
}

/** 시세가 바뀌면 상품 가격 미리보기까지 함께 낡는다 — 둘 다 무효화한다. */
function invalidatePriceDependents(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: adminKeys.prices() });
  void queryClient.invalidateQueries({ queryKey: adminKeys.products() });
}

export function useDeclarePrice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.declarePrice,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

export function useUpdatePolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.updatePolicy,
    // 정책 변경은 이미 발행된 시세를 바꾸지 않는다. 그래도 정책 목록은 새로 읽어야 한다.
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.policies() });
    },
  });
}

/**
 * 자동/수동 고시 전환.
 *
 * <p>정책 목록만 무효화하는 {@link useUpdatePolicy} 와 달리 시세에 딸린 것을 전부
 * 무효화한다 — 자동으로 켜는 순간 다음 수집분이 바로 발행될 수 있고, 그러면
 * 현재 시세·상품가·이력이 다 같이 움직인다.
 */
/**
 * 살 때·팔 때 동시 고시.
 *
 * <p>시세가 바뀌면 상품가·이력·현재시세가 모두 따라 움직이므로 전부 무효화한다.
 */
export function useDeclarePair() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.declarePair,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

/** 자동고시 시작 — 보정값과 전환이 한 번에 간다. */
export function useStartAutoQuote() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.startAutoQuote,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

export function useSwitchQuoteMode() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.switchQuoteMode,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

/** 짝(살 때·팔 때)을 함께 승인한다. */
export function useApprovePendingBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.approvePendingBatch,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

/** 짝을 함께 반려한다. */
export function useRejectPendingBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.rejectPendingBatch,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

export function useApprovePending() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      pricesApi.approvePending(id, reason),
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

export function useRejectPending() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      pricesApi.rejectPending(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.prices() });
    },
  });
}
