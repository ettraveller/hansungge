import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';
import type { Order } from '@/types/order';
import type { AdminOrderSearch, AdminOrderSummary, Carrier, Shipment } from '@/types/order-admin';

export function listOrders(search: AdminOrderSearch = {}) {
  return adminApi.get<PageResponse<AdminOrderSummary>>('/api/admin/orders', {
    query: {
      status: search.status,
      // false 를 보내면 "발송 대기가 아닌 것만" 으로 읽힐 여지가 있다. 켤 때만 보낸다.
      awaitingShipment: search.awaitingShipment ? true : undefined,
      keyword: search.keyword,
      from: search.from,
      to: search.to,
      page: search.page ?? 0,
      size: search.size ?? 20,
    },
  });
}

/** 목록 상단의 "발송 대기 N건". 판정 기준은 목록 필터와 같다. */
export function awaitingCount() {
  return adminApi.get<{ count: number }>('/api/admin/orders/awaiting-count');
}

export function getOrder(id: number) {
  return adminApi.get<Order>(`/api/admin/orders/${id}`);
}

export function getShipment(id: number) {
  return adminApi.get<Shipment>(`/api/admin/orders/${id}/shipment`);
}

export function markPreparing(id: number) {
  return adminApi.post<Order>(`/api/admin/orders/${id}/prepare`);
}

/**
 * 송장번호를 넣고 발송 처리.
 *
 * <p>하이픈·공백이 섞여도 된다 — 서버가 숫자만 남긴다. 이미 발송된 주문에 다시
 * 보내면 번호만 바뀌고 발송 시각은 처음 값을 지킨다.
 */
export function ship(id: number, trackingNo: string, carrier: Carrier = 'KOREA_POST') {
  return adminApi.put<Shipment>(`/api/admin/orders/${id}/shipment`, {
    body: { carrier, trackingNo },
  });
}

export function markDelivered(id: number) {
  return adminApi.post<Shipment>(`/api/admin/orders/${id}/deliver`);
}
