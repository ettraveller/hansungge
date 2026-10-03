import { authed } from '@/api/authedClient';
import type { DeliveryAddress, DeliveryAddressInput } from '@/types/order';

/**
 * 배송지 주소록 API. 전 구간 로그인 필요.
 *
 * <p>주문서가 쓰는 편의 장치다. 주문이 만들어질 때 주소는 주문 쪽으로 복사되므로,
 * 여기서 지워도 지나간 주문은 그대로다.
 */

export function fetchAddresses() {
  return authed<DeliveryAddress[]>('/api/v1/me/addresses');
}

export function createAddress(input: DeliveryAddressInput) {
  return authed<DeliveryAddress>('/api/v1/me/addresses', { method: 'POST', body: input });
}

export function updateAddress(id: number, input: DeliveryAddressInput) {
  return authed<DeliveryAddress>(`/api/v1/me/addresses/${id}`, { method: 'PUT', body: input });
}

export function makeDefaultAddress(id: number) {
  return authed<void>(`/api/v1/me/addresses/${id}/default`, { method: 'POST' });
}

export function deleteAddress(id: number) {
  return authed<void>(`/api/v1/me/addresses/${id}`, { method: 'DELETE' });
}
