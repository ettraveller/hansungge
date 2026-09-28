import { authed } from '@/api/authedClient';
import { request } from '@/api/client';
import type { Order, OrderCreateInput, OrderPage, PaymentConfig } from '@/types/order';

/**
 * 주문·결제 API.
 *
 * <p><b>금액과 상품 목록을 보내지 않는다.</b> 무엇을 사는지는 서버의 장바구니에서 읽고,
 * 얼마인지는 서버가 그 시점 시세로 다시 계산한다 (원칙 P1). 그래서 이 파일에 금액
 * 파라미터가 하나도 없다 — 결제 승인에서조차 없다.
 */

/** 장바구니 전체를 주문서로 만든다. 받는 것은 배송지뿐이다. */
export function createOrder(input: OrderCreateInput) {
  return authed<Order>('/api/v1/orders', { method: 'POST', body: input });
}

export function fetchOrders(page = 0, size = 10) {
  return authed<OrderPage>(`/api/v1/orders?page=${page}&size=${size}`);
}

export function fetchOrder(orderNo: string) {
  return authed<Order>(`/api/v1/orders/${encodeURIComponent(orderNo)}`);
}

export function cancelOrder(orderNo: string) {
  return authed<Order>(`/api/v1/orders/${encodeURIComponent(orderNo)}/cancel`, { method: 'POST' });
}

/** 결제 설정. 로그인 전에도 부를 수 있어 authed 가 아니다. */
export function fetchPaymentConfig() {
  return request<PaymentConfig>('/api/v1/payments/config');
}

/** 결제창을 띄우기 직전 — 주문을 "결제 진행 중"으로 옮긴다. */
export function readyPayment(orderNo: string) {
  return authed<Order>(`/api/v1/payments/${encodeURIComponent(orderNo)}/ready`, { method: 'POST' });
}

/**
 * 승인. <b>금액을 보내지 않는다.</b>
 *
 * 토스는 성공 주소에 amount 도 붙여 보내지만 그것은 주소창에서 고칠 수 있는 값이다.
 * 서버가 주문번호로 DB 금액을 찾아 그것으로 승인한다.
 */
export function confirmPayment(input: { orderNo: string; paymentKey: string }) {
  return authed<Order>('/api/v1/payments/confirm', { method: 'POST', body: input });
}

/** 결제창에서 취소·실패했을 때. 주문은 살아 있어 다른 수단으로 다시 낼 수 있다. */
export function failPayment(orderNo: string, reason?: string) {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return authed<void>(`/api/v1/payments/${encodeURIComponent(orderNo)}/fail${query}`, {
    method: 'POST',
  });
}
