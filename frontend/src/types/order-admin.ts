import type { OrderStatus, Shipment } from '@/types/order';

/*
 * 배송 타입(Shipment·ShipmentStatus·Carrier)은 `types/order.ts` 에 있다.
 * 백엔드가 관리자와 고객에게 같은 모양을 주므로, 여기 두면 고객 화면이 관리자
 * 타입을 import 하게 된다 — 그러면 한쪽만 고치는 날 두 화면의 모양이 갈라진다.
 */
export type { Carrier, Shipment, ShipmentStatus } from '@/types/order';

/** 관리자 주문 목록의 한 줄. 상세와 달리 화면에 쓰는 것만 담겨 온다. */
export interface AdminOrderSummary {
  id: number;
  orderNo: string;
  orderedAt: string;
  status: OrderStatus;
  statusLabel: string;
  receiverName: string;
  orderName: string;
  itemCount: number;
  totalAmount: number;
  shipment: Shipment;
  /**
   * 발송 대기인가.
   *
   * 「결제완료·준비중 + 송장번호 없음」이라는 조합을 화면이 다시 판단하지 않는다 —
   * 백엔드와 갈라지면 목록 줄 수와 상단 건수가 어긋난다.
   */
  awaitingShipment: boolean;
}

export interface AdminOrderSearch {
  status?: OrderStatus | null;
  awaitingShipment?: boolean | null;
  keyword?: string | null;
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
}
