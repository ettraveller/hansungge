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
  /**
   * 대표이미지 키. 금액·상품명과 달리 <b>스냅샷이 아니다</b> — 지금 상품의 사진이다.
   *
   * 사진까지 굳혀 두면 상품 사진을 바꿀 때마다 지나간 주문이 옛 사진에 묶인다.
   * 상품이 지워졌거나 사진이 없으면 없다.
   */
  thumbnailKey?: string | null;
}

export interface Order {
  orderNo: string;
  status: OrderStatus;
  statusLabel: string;

  itemsAmount: number;
  shippingFee: number;
  discountAmount: number;
  totalAmount: number;

  /**
   * 부가세액(원). 과세 상품의 판매가는 부가세 포함가라(V38) 이 금액은
   * <b>결제 금액 안에 들어 있다</b> - 더해지는 값이 아니다. 면세 주문이면 0.
   */
  vatAmount: number;
  /** 공급가액(원) = 상품 합계 - 부가세액 */
  supplyAmount: number;

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

  /** 고객이 고른 결제수단 */
  paymentMethod: PaymentMethod;
  /** 통장에 찍히는 입금자명. 무통장 주문에만 있다 — 주문자와 다를 수 있다 */
  depositorName?: string | null;
  /** 입금 기한. <b>지나면 주문이 자동 취소된다</b> (2026-11-05). 고객 화면에도 적혀 있다 */
  depositDueAt?: string | null;
}

/**
 * 결제수단. 백엔드 PaymentMethod 와 값이 같아야 한다.
 *
 * <p>무엇을 고를 수 있는지는 <b>서버가 정한다</b> ({@link Cart.cardPayable}).
 * 순금·순은·골드바·24K·시세 연동 상품은 카드사가 온라인 거래를 제한해 PG 심사를
 * 통과하지 못한다 (2026-11-03 토스페이먼츠 회신).
 */
export type PaymentMethod = 'EASY_PAY' | 'BANK_TRANSFER';

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  EASY_PAY: '간편결제',
  BANK_TRANSFER: '무통장입금',
};

/** 배송 상태. 백엔드 ShipmentStatus 와 값이 같아야 한다. */
export type ShipmentStatus = 'PENDING' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED';

/** 택배사. 지금은 우체국택배 하나뿐이다. */
export type Carrier = 'KOREA_POST';

/**
 * 배송 정보. <b>관리자와 고객이 같은 모양으로 받는다.</b>
 *
 * <p>아직 발송 전이면 서버가 {@code status: 'PENDING'} 으로 채워 보낸다 — 화면이
 * 매번 널 검사를 하지 않도록.
 *
 * <p>{@link trackingUrl} 에 송장번호가 붙어 있지 않다. 우체국이 번호를 GET 으로 받는
 * 공개 주소를 보장하지 않아서, 붙인 링크는 어느 날 조용히 빈 화면이 된다. 조회
 * 페이지로 보내고 번호는 화면에서 복사하게 한다.
 */
export interface Shipment {
  status: ShipmentStatus;
  statusLabel: string;
  carrier?: Carrier | null;
  carrierName?: string | null;
  trackingUrl?: string | null;
  trackingNo?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  /** null 이면 배송조회가 꺼져 있거나 아직 부른 적 없다 */
  trackingSyncedAt?: string | null;
  trackingStatus?: string | null;
  /**
   * 진행 단계. <b>JSON 문자열로 온다</b> — 객체가 아니다.
   *
   * <p>DB 에 JSONB 한 칸으로 넣고 그대로 꺼내 보내기 때문이다. 택배사마다 모양이
   * 다른 데이터라 컬럼으로 펼치면 택배사가 늘 때마다 마이그레이션이 필요하다.
   * 화면은 {@code parseTrackingSteps} 로 푼다.
   */
  trackingDetail?: string | null;
}

/** 배송 진행 한 단계. 우체국이 준 표현을 그대로 담는다 — 우리가 해석하지 않는다. */
export interface TrackingStep {
  /** 그 일이 일어난 시각. 택배사가 날짜를 못 읽히게 주면 null */
  at?: string | null;
  /** 어디서. 택배사가 주지 않으면 null */
  where?: string | null;
  /** 무슨 일이. "집화완료", "배송출발" 같은 택배사의 표현 */
  what?: string | null;
}

/**
 * 주문 상태 묶음. 백엔드 OrderStatusGroup 과 값이 같아야 한다.
 *
 * `SHIPPING` 은 `SHIPPED` + `DELIVERED` 다. 마이페이지 요약이 배송 중과 배송 완료를
 * 따로 세므로 거르는 쪽에도 둘이 따로 있어야 한다 — 숫자와 목록이 어긋나지 않게.
 */
export type OrderStatusGroup =
  | 'ALL'
  | 'AWAITING_PAYMENT'
  | 'PREPARING'
  | 'SHIPPING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED';

export interface OrderListQuery {
  page?: number;
  size?: number;
  group?: OrderStatusGroup;
  /** ISO 시각. 이 시각부터 */
  from?: string;
  /** ISO 시각. 이 시각 직전까지 */
  to?: string;
}

export interface OrderPage {
  content: Order[];
  totalElements: number;
  totalPages: number;
  number: number;
  first: boolean;
  last: boolean;
}

/** 무통장입금 받을 계좌. 설정 전이면 서버가 null 로 준다 */
export interface BankAccount {
  bank: string;
  number: string;
  holder: string;
}

export interface OrderCreateInput {
  /** 청약철회 제한 안내 동의. 서버가 false 면 거절한다 */
  withdrawalConsent: boolean;
  /** 동의한 환불규정 판 번호. 읽는 사이 개정됐으면 서버가 409(REFUND_POLICY_CHANGED)를 준다 */
  refundPolicyId: number;
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2?: string;
  orderMemo?: string;
  /** 결제수단. 허용 여부는 서버가 다시 따진다 — 안 되는 조합이면 409 */
  paymentMethod: PaymentMethod;
  /** 입금자명. 무통장일 때 필수 */
  depositorName?: string;
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
  /**
   * 무통장입금 받을 계좌. 설정 전이면 null 이고, 그때는 무통장 주문을 받지 않는다.
   *
   * <p>주문마다 들고 다니지 않는다 — 가게의 계좌는 주문의 속성이 아니라 설정이다.
   */
  bankAccount?: BankAccount | null;
  /** 입금 안내 기한(시간). 지나도 자동취소하지 않는다 */
  depositDueHours: number;
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
