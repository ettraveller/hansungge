import type { OrderStatus } from '@/types/order';

/** 배송 상태. 백엔드 ShipmentStatus 와 값이 같아야 한다. */
export type ShipmentStatus = 'PENDING' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED';

/** 택배사. 지금은 우체국택배 하나뿐이다. */
export type Carrier = 'KOREA_POST';

/**
 * 배송 정보.
 *
 * <p>아직 발송 전이면 서버가 {@code status: 'PENDING'} 으로 채워 보낸다 — 화면이
 * 매번 널 검사를 하지 않도록.
 */
export interface Shipment {
  status: ShipmentStatus;
  statusLabel: string;
  carrier?: Carrier | null;
  carrierName?: string | null;
  /** 택배사 조회 페이지. 송장번호는 붙어 있지 않다 — 우체국이 그 형태를 보장하지 않는다 */
  trackingUrl?: string | null;
  trackingNo?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  /** null 이면 배송조회가 꺼져 있거나 아직 부른 적 없다 */
  trackingSyncedAt?: string | null;
  trackingStatus?: string | null;
  /** 진행 단계(JSON 문자열). 조회가 꺼져 있으면 null */
  trackingDetail?: string | null;
}

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
