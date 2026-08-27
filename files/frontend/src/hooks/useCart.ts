'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import * as cartApi from '@/api/cart';
import { useUserSession } from '@/hooks/useUserSession';
import type { Cart } from '@/types/cart';

export const cartKeys = {
  all: ['cart'] as const,
  detail: () => [...cartKeys.all, 'detail'] as const,
  count: () => [...cartKeys.all, 'count'] as const,
};

/**
 * 장바구니 조회.
 *
 * <p>{@code staleTime: 0} 이다. 장바구니 금액은 <b>시세에 따라 매번 달라지는 값</b>이라
 * 캐시해 두면 화면에 옛 가격이 남는다. 다시 열 때마다 서버가 계산한 지금 값을 받는다.
 */
export function useCart() {
  const { isAuthenticated } = useUserSession();
  return useQuery({
    queryKey: cartKeys.detail(),
    queryFn: cartApi.fetchCart,
    enabled: isAuthenticated,
    staleTime: 0,
  });
}

/** 헤더 배지. 가격 계산을 돌리지 않는 가벼운 조회라 조금 오래 두어도 된다. */
export function useCartCount() {
  const { isAuthenticated } = useUserSession();
  return useQuery({
    queryKey: cartKeys.count(),
    queryFn: cartApi.fetchCartCount,
    enabled: isAuthenticated,
    staleTime: 30_000,
  });
}

/** 변경 API 는 장바구니 전체를 돌려주므로 그대로 캐시에 넣는다 — 재조회가 필요 없다. */
function useCartMutation<TArgs>(fn: (args: TArgs) => Promise<Cart>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKeys.detail(), cart);
      queryClient.setQueryData(cartKeys.count(), { count: cart.itemCount });
    },
  });
}

export function useAddToCart() {
  return useCartMutation(cartApi.addToCart);
}

export function useChangeCartQuantity() {
  return useCartMutation(({ itemId, quantity }: { itemId: number; quantity: number }) =>
    cartApi.changeCartQuantity(itemId, quantity),
  );
}

export function useRemoveCartItem() {
  return useCartMutation((itemId: number) => cartApi.removeCartItem(itemId));
}

export function useClearCart() {
  return useCartMutation(() => cartApi.clearCart());
}
