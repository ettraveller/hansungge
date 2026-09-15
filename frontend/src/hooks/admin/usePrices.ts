'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as pricesApi from '@/api/admin/prices';
import { adminKeys } from './keys';
import type { MetalType, PriceSide } from '@/types/price';

/**
 * 시세는 관리자 화면에서 가장 자주 바뀌는 값이다. 다만 자동 폴링은 두지 않는다 —
 * 관리자가 보고 있는 숫자가 조작 중에 바뀌면 잘못된 값을 확인하고 누를 수 있다.
 * 갱신은 고시·설정 변경 후 명시적 무효화로만 일어난다.
 */
export function useCurrentPrices() {
  return useQuery({
    queryKey: adminKeys.currentPrices(),
    queryFn: pricesApi.currentPrices,
    staleTime: 10_000,
  });
}

export function usePriceHistory(metal: MetalType, side: PriceSide, page: number) {
  return useQuery({
    queryKey: adminKeys.priceHistory(metal, side, page),
    queryFn: () => pricesApi.priceHistory(metal, side, page),
  });
}

export function useDerivations() {
  return useQuery({ queryKey: adminKeys.derivations(), queryFn: pricesApi.derivations });
}

/**
 * 시세가 바뀌면 상품 가격 미리보기까지 함께 낡는다 — 둘 다 무효화한다.
 * 환산 설정도 시세 키 아래에 있어 같이 새로 읽힌다.
 */
function invalidatePriceDependents(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: adminKeys.prices() });
  void queryClient.invalidateQueries({ queryKey: adminKeys.products() });
}

/** 고시 미리보기. 아무것도 바꾸지 않으므로 무효화하지 않는다. */
export function usePreviewPair() {
  return useMutation({ mutationFn: pricesApi.previewPair });
}

/** 살 때·팔 때 동시 고시. 24K 면 18K·14K 도 함께 움직이므로 전부 무효화한다. */
export function useDeclarePair() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.declarePair,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}

/** 환산 비율 미리보기. 아무것도 바꾸지 않는다. */
export function usePreviewDerivation() {
  return useMutation({ mutationFn: pricesApi.previewDerivation });
}

/** 환산 설정 변경. 곧바로 다시 고시되므로 시세에 딸린 것을 전부 무효화한다. */
export function useUpdateDerivation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pricesApi.updateDerivation,
    onSuccess: () => invalidatePriceDependents(queryClient),
  });
}
