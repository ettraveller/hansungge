'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as api from '@/api/interest';
import { useUserSession } from '@/hooks/useUserSession';

/**
 * 찜한 상품 · 최근 본 상품.
 *
 * <p>찜하기는 <b>누른 즉시 하트가 바뀐다</b>(낙관적 갱신). 서버 응답을 기다리면 연타했을 때
 * 하트가 깜빡이고, 고객은 눌렸는지 아닌지 알 수 없다. 실패하면 되돌린다.
 */

export const interestKeys = {
  summary: ['mypage', 'summary'] as const,
  wishlist: ['mypage', 'wishlist'] as const,
  wishlisted: (productId: number) => ['mypage', 'wishlisted', productId] as const,
  recentlyViewed: ['mypage', 'recently-viewed'] as const,
};

export function useMyPageSummary() {
  const { isAuthenticated } = useUserSession();
  return useQuery({
    queryKey: interestKeys.summary,
    queryFn: () => api.fetchMyPageSummary(),
    enabled: isAuthenticated,
  });
}

export function useWishlist() {
  const { isAuthenticated } = useUserSession();
  return useQuery({
    queryKey: interestKeys.wishlist,
    queryFn: () => api.fetchWishlist(),
    enabled: isAuthenticated,
  });
}

export function useRecentlyViewed(size = 20) {
  const { isAuthenticated } = useUserSession();
  return useQuery({
    queryKey: interestKeys.recentlyViewed,
    queryFn: () => api.fetchRecentlyViewed(size),
    enabled: isAuthenticated,
  });
}

/** 상품 하나의 찜 여부. 로그인 전에는 묻지 않는다 — 비회원에게는 하트를 그리지 않는다. */
export function useIsWishlisted(productId: number | null) {
  const { isAuthenticated } = useUserSession();
  return useQuery({
    queryKey: interestKeys.wishlisted(productId ?? 0),
    queryFn: () => api.checkWishlist(productId as number),
    enabled: isAuthenticated && productId != null,
    select: (data) => data.wishlisted,
  });
}

export function useToggleWishlist() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, next }: { productId: number; next: boolean }) =>
      next ? api.addWishlist(productId) : api.removeWishlist(productId),

    // 누른 즉시 하트를 바꾼다.
    onMutate: async ({ productId, next }) => {
      const key = interestKeys.wishlisted(productId);
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData(key);
      queryClient.setQueryData(key, { wishlisted: next });
      return { key, previous };
    },

    onError: (_error, _vars, context) => {
      // 실패했으면 되돌린다. 그러지 않으면 화면은 찜했다는데 서버에는 없다.
      if (context) {
        queryClient.setQueryData(context.key, context.previous);
      }
    },

    onSettled: (_data, _error, { productId }) => {
      queryClient.invalidateQueries({ queryKey: interestKeys.wishlisted(productId) });
      queryClient.invalidateQueries({ queryKey: interestKeys.wishlist });
      queryClient.invalidateQueries({ queryKey: interestKeys.summary });
    },
  });
}

export function useClearRecentlyViewed() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.clearRecentlyViewed(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: interestKeys.recentlyViewed }),
  });
}

/**
 * 상품 상세에서 "봤다"고 알린다.
 *
 * <p>기록이 실패해도 <b>아무 일도 일어나지 않는다.</b> 고객이 보러 온 것은 상품이지
 * 기록이 아니다. 그래서 오류를 삼킨다.
 */
export function useMarkViewed() {
  return useMutation({
    mutationFn: (productId: number) => api.markViewed(productId),
    onError: () => {},
  });
}
