import { authed } from '@/api/authedClient';
import type { MyPageSummary } from '@/types/interest';
import type { ProductSummary } from '@/types/product';

/**
 * 찜한 상품 · 최근 본 상품 · 마이페이지 요약.
 *
 * <p>금액을 보내거나 받아 저장하지 않는다. 목록의 금액은 서버가 <b>열 때마다 다시 계산한</b>
 * 값이다 — 찜한 시점 금액을 보관하면 다음 날이면 틀린 값이 된다.
 */

export function fetchMyPageSummary() {
  return authed<MyPageSummary>('/api/v1/me/summary');
}

export function fetchWishlist() {
  return authed<ProductSummary[]>('/api/v1/me/wishlist');
}

export function addWishlist(productId: number) {
  return authed<void>(`/api/v1/me/wishlist/${productId}`, { method: 'POST' });
}

export function removeWishlist(productId: number) {
  return authed<void>(`/api/v1/me/wishlist/${productId}`, { method: 'DELETE' });
}

export function checkWishlist(productId: number) {
  return authed<{ wishlisted: boolean }>(`/api/v1/me/wishlist/${productId}`);
}

export function fetchRecentlyViewed(size = 20) {
  return authed<ProductSummary[]>(`/api/v1/me/recently-viewed?size=${size}`);
}

/** 상품 상세를 열 때 부른다. 실패해도 화면에 영향을 주지 않는다. */
export function markViewed(productId: number) {
  return authed<void>(`/api/v1/me/recently-viewed/${productId}`, { method: 'POST' });
}

export function clearRecentlyViewed() {
  return authed<void>('/api/v1/me/recently-viewed', { method: 'DELETE' });
}
