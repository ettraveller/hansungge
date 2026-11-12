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
  /**
   * 관리자가 상세를 연 적이 있는가.
   *
   * false 면 미확인 주문이다 — 줄에 빨간 점이 붙고 상태 탭 배지에 세어진다.
   * 상세를 열면 true 가 되고, 카드 결제 도착·고객 취소가 생기면 다시 false 로 돌아온다.
   */
  checked: boolean;
}

/**
 * 상태 탭별 미확인 건수. 필드가 탭 구성과 1:1 이다.
 *
 * 숫자의 뜻은 「그 탭을 눌렀을 때 나오는 목록 중 미확인인 줄 수」 — 세는 기준은
 * 백엔드 한 곳에 있다. 화면이 조합해 만들면 배지와 목록이 어긋난다.
 */
export interface AdminOrderUncheckedCounts {
  awaiting: number;
  all: number;
  paid: number;
  shipped: number;
  delivered: number;
  cancelled: number;
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
