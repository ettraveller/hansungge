/**
 * 주문·결제 타입. 백엔드 DTO 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 non_null 직렬화라 null 필드를 응답에서 생략한다.
 * `| null` 로 적힌 값은 런타임에 `undefined` 로 도착할 수 있으므로 비교는 `== null` 로 한다.
 */

import type { MetalType } from '@/types/price';

export type OrderStatus =
  | 'ORDER_CREATED'
  | 'PAYMENT_PENDING'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED'
  | 'REFUND_PENDING'
  | 'REFUNDED';

/**
 * 주문 항목 — <b>주문 시점 스냅샷</b>이다.
 *
 * 장바구니와 달리 다시 계산하지 않는다. 이미 낸 돈이 볼 때마다 달라지면 안 된다.
 */
export interface OrderItem {
  productId: number;
  /** 주문한 규격. 같은 규격으로 다시 담을 때 쓴다. 옵션 없는 상품이면 null */
  optionId?: number | null;
  productName: string;
  optionName?: string | null;
  metalType?: MetalType | null;
  weightG?: number | null;
  /** 주문 시각에 적용된 시세(원/g). 시세 미연동이면 null */
  marketPrice?: number | null;
  finalUnitPrice: number;
  quantity: number;
  finalAmount: number;
}

export interface Order {
  orderNo: string;
  status: OrderStatus;
  statusLabel: string;

  itemsAmount: number;
  shippingFee: number;
  discountAmount: number;
  totalAmount: number;

  /** 결제창·목록에 쓰는 한 줄 이름 — "순금 골드바 3.75g 외 2건" */
  orderName: string;
  items: OrderItem[];

  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2?: string | null;
  orderMemo?: string | null;

  orderedAt: string;
  payable: boolean;
  cancellable: boolean;
}

export interface OrderPage {
  content: Order[];
  totalElements: number;
  totalPages: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface OrderCreateInput {
  /** 청약철회 제한 안내 동의. 서버가 false 면 거절한다 */
  withdrawalConsent: boolean;
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2?: string;
  orderMemo?: string;
}

/**
 * 결제 설정.
 *
 * `clientKey` 는 브라우저에 나가라고 있는 공개값이다. 시크릿 키는 서버 밖으로 나오지 않는다.
 */
export interface PaymentConfig {
  available: boolean;
  clientKey: string;
  /** 테스트 키인가 — 화면에 "실제 결제가 아닙니다" 안내를 띄운다 */
  testMode: boolean;
}

/**
 * 저장해 둔 배송지.
 *
 * <p>주문은 주소를 자기 칸에 복사해 간다. 그래서 여기를 고치거나 지워도 지나간 주문의
 * 배송지는 바뀌지 않는다 — 어디로 보냈는지는 분쟁의 근거다.
 */
export interface DeliveryAddress {
  id: number;
  /** 별칭("집", "회사"). 없을 수 있다 */
  label: string | null;
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2: string | null;
  isDefault: boolean;
}

export interface DeliveryAddressInput {
  label?: string;
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2?: string;
  setDefault: boolean;
}
