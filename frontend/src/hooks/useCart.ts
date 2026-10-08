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

/** 재주문 한 줄. 주문 항목에서 그대로 뽑는다 */
export interface ReorderLine {
  productId: number;
  optionId?: number | null;
  quantity: number;
}

/**
 * 주문에 들어 있던 상품을 한꺼번에 장바구니로 옮긴다 (재주문).
 *
 * <h2>하나씩 차례로 보낸다</h2>
 * 장바구니 API 는 매번 <b>장바구니 전체</b>를 돌려준다. 동시에 보내면 먼저 끝난 응답이
 * 나중 것을 덮어써, 방금 담은 줄이 화면에서 사라진 것처럼 보인다.
 *
 * <h2>하나가 막혀도 멈추지 않는다</h2>
 * 품절·판매중지된 줄은 서버가 거절한다. 거기서 끊으면 나머지도 못 담는다. 끝까지 시도한 뒤
 * 몇 개가 담겼는지 돌려주고, 부른 쪽이 그 숫자로 안내한다.
 *
 * <p>규격({@code optionId})을 함께 보낸다. 상품 id 만 보내면 7.5g 을 샀던 사람이
 * 3.75g 을 담게 된다.
 */
export function useReorder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (lines: ReorderLine[]) => {
      let cart: Cart | null = null;
      let added = 0;

      for (const line of lines) {
        try {
          cart = await cartApi.addToCart(line);
          added += 1;
        } catch {
          // 어느 줄이 막혔는지는 장바구니 화면이 이유와 함께 보여준다. 여기서는 숫자만 센다.
        }
      }

      return { cart, added, failed: lines.length - added };
    },
    onSuccess: ({ cart }) => {
      if (!cart) return;
      queryClient.setQueryData(cartKeys.detail(), cart);
      queryClient.setQueryData(cartKeys.count(), { count: cart.itemCount });
    },
  });
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
