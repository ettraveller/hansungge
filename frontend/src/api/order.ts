import { authed } from '@/api/authedClient';
import { request } from '@/api/client';
import type { Cart } from '@/types/cart';
import type {
  Order,
  OrderCreateInput,
  OrderListQuery,
  OrderPage,
  PaymentConfig,
  Shipment,
} from '@/types/order';

/**
 * 주문·결제 API.
 *
 * <p><b>금액과 상품 목록을 보내지 않는다.</b> 무엇을 사는지는 서버의 장바구니에서 읽고,
 * 얼마인지는 서버가 그 시점 시세로 다시 계산한다 (원칙 P1). 그래서 이 파일에 금액
 * 파라미터가 하나도 없다 — 결제 승인에서조차 없다.
 */

/** 장바구니 전체를(directItem 이 있으면 그 한 품목만) 주문서로 만든다. */
export function createOrder(input: OrderCreateInput) {
  return authed<Order>('/api/v1/orders', { method: 'POST', body: input });
}

/** 상세의 「바로 구매」가 남기고 주문서가 읽는 품목. */
export interface DirectOrderDraft {
  productId: number;
  optionId: number | null;
  quantity: number;
}

/**
 * 바로 구매 품목을 sessionStorage 로 전달한다.
 *
 * <p>URL 쿼리에 싣지 않는 이유 — 주소를 복사해 보내거나 북마크하면 남의 브라우저에서
 * 의미 없는 주문서가 열린다. 같은 탭에서 상세 → 주문서로 건너가는 한 걸음만 살면 된다.
 */
export const DIRECT_ORDER_KEY = 'hsg.direct-order';

export function saveDirectDraft(draft: DirectOrderDraft): void {
  try {
    sessionStorage.setItem(DIRECT_ORDER_KEY, JSON.stringify(draft));
  } catch {
    // 시크릿 모드 등에서 막혀도 주문서가 장바구니 흐름으로 동작한다.
  }
}

export function readDirectDraft(): DirectOrderDraft | null {
  try {
    const raw = sessionStorage.getItem(DIRECT_ORDER_KEY);
    if (!raw) return null;
    const draft = JSON.parse(raw) as DirectOrderDraft;
    return typeof draft?.productId === 'number' && typeof draft?.quantity === 'number'
      ? draft
      : null;
  } catch {
    return null;
  }
}

export function clearDirectDraft(): void {
  try {
    sessionStorage.removeItem(DIRECT_ORDER_KEY);
  } catch {
    /* 지우지 못해도 다음 바로 구매가 덮어쓴다 */
  }
}

/**
 * 바로 구매 미리보기 — 장바구니 조회와 같은 모양으로 온다.
 *
 * <p>주문서가 두 흐름(장바구니 전체 / 바로 구매 한 품목)을 한 코드로 그리기 위한 것이다.
 */
export function fetchDirectPreview(draft: DirectOrderDraft) {
  const params = new URLSearchParams({
    productId: String(draft.productId),
    quantity: String(draft.quantity),
  });
  if (draft.optionId != null) params.set('optionId', String(draft.optionId));
  return authed<Cart>(`/api/v1/orders/direct-preview?${params.toString()}`);
}

/**
 * 내 주문 목록.
 *
 * <p>거르기는 서버가 한다. 한 장만 받아 화면에서 거르면 탭에 적힌 개수(서버 총계)와
 * 목록이 어긋난다 — "결제 대기 3" 인데 목록이 비는 식이다.
 */
export function fetchOrders(query: OrderListQuery = {}) {
  const params = new URLSearchParams();
  params.set('page', String(query.page ?? 0));
  params.set('size', String(query.size ?? 10));
  if (query.group && query.group !== 'ALL') params.set('group', query.group);
  if (query.from) params.set('from', query.from);
  if (query.to) params.set('to', query.to);

  return authed<OrderPage>(`/api/v1/orders?${params.toString()}`);
}

export function fetchOrder(orderNo: string) {
  return authed<Order>(`/api/v1/orders/${encodeURIComponent(orderNo)}`);
}

/**
 * 내 주문의 배송 정보.
 *
 * <p>주문 상세와 따로 부른다. 목록에서도 쓸 수 있게 묶지 않은 것이고, 발송 뒤에는
 * 이것만 다시 물어 송장번호를 갱신할 수 있다.
 */
export function fetchShipment(orderNo: string) {
  return authed<Shipment>(`/api/v1/orders/${encodeURIComponent(orderNo)}/shipment`);
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
