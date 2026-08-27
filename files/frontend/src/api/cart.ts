import { authed } from '@/api/authedClient';
import type { Cart } from '@/types/cart';

/**
 * 장바구니 API. 전 구간 로그인 필요.
 *
 * <p><b>금액을 보내지 않는다.</b> 서버가 담는 순간 계산해 기록하고, 조회할 때마다 현재
 * 시세로 다시 계산한다 (원칙 P1). 그래서 이 파일에 가격 파라미터가 하나도 없다.
 *
 * <p>변경 API 는 모두 장바구니 <b>전체</b>를 돌려준다. 한 줄만 받아 화면에서 합계를
 * 다시 맞추면 서버 계산과 갈라진다.
 */

export function fetchCart() {
  return authed<Cart>('/api/v1/cart');
}

/** 헤더 배지용. 가격 계산 없이 줄 수만 센다. */
export function fetchCartCount() {
  return authed<{ count: number }>('/api/v1/cart/count');
}

export function addToCart(input: { productId: number; optionId?: number | null; quantity: number }) {
  return authed<Cart>('/api/v1/cart/items', { method: 'POST', body: input });
}

/** 증감이 아니라 절대 수량이다. 중복 요청에도 수량이 두 번 늘지 않는다. */
export function changeCartQuantity(itemId: number, quantity: number) {
  return authed<Cart>(`/api/v1/cart/items/${itemId}`, { method: 'PATCH', body: { quantity } });
}

export function removeCartItem(itemId: number) {
  return authed<Cart>(`/api/v1/cart/items/${itemId}`, { method: 'DELETE' });
}

export function clearCart() {
  return authed<Cart>('/api/v1/cart', { method: 'DELETE' });
}
