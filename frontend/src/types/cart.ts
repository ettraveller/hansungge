/**
 * 장바구니 타입. 백엔드 DTO 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 {@code non_null} 직렬화라 null 필드를 응답에서 생략한다.
 * {@code | null} 로 적힌 값은 런타임에 {@code undefined} 로 도착할 수 있으므로
 * 비교는 {@code == null} (느슨한 비교)로 한다.
 */

import type { MetalType } from '@/types/price';

/**
 * 장바구니 한 줄.
 *
 * <p>{@link unitPrice} 는 <b>지금 다시 계산한</b> 값이고 {@link priceAtAdd} 는 담을 때 값이다.
 * 두 값의 차이는 {@link priceDelta} 로 서버가 이미 내준다 — 화면에서 빼지 않는다 (원칙 P1).
 *
 * <p>가격을 낼 수 없으면 금액 자리가 <b>비어 있고</b> {@link priceText} 가 "시세문의"다.
 * 0 이 오는 경로는 없다.
 */
export interface CartItem {
  id: number;
  productId: number;
  optionId: number;
  productName: string;
  optionName: string;
  thumbnailKey?: string | null;
  metalType?: MetalType | null;
  weightG?: number | null;
  quantity: number;

  /** 현재 단가(원). 표시 불가면 null */
  unitPrice?: number | null;
  /** 현재 줄 합계(단가 × 수량). 표시 불가면 null */
  lineAmount?: number | null;
  priceText: string;

  priceAtAdd: number;
  priceChanged: boolean;
  /** 현재 - 담을 때. 오르면 양수 */
  priceDelta?: number | null;

  orderable: boolean;
  unavailableReason?: string | null;
  stockShortage: boolean;
  /** 재고 부족일 때만 채워진다 */
  availableStock?: number | null;
  /**
   * 카드·간편결제로 살 수 있는 줄인가.
   *
   * false 면 이 줄 하나 때문에 주문 전체가 무통장입금으로 간다 — 화면이 어느 줄
   * 때문인지 짚어 줄 수 있어야 한다.
   */
  cardPayable: boolean;
}

/**
 * 장바구니 전체.
 *
 * <p>{@link subtotal} 은 <b>주문 가능한 줄만</b> 더한 값이다. 이 값도 결제 금액이 아니며,
 * 주문을 만들 때 서버가 처음부터 다시 계산한다.
 */
export interface Cart {
  items: CartItem[];
  itemCount: number;
  totalQuantity: number;
  subtotal?: number | null;
  subtotalText: string;
  unavailableCount: number;
  priceChangedCount: number;
  orderable: boolean;
  /**
   * 이 장바구니를 카드·간편결제로 결제할 수 있는가.
   *
   * <p>한 줄이라도 안 되면 전체가 안 된다 — 순금·순은·골드바·24K·시세 연동 상품은
   * 카드사가 온라인 거래를 제한해 PG 심사를 통과하지 못한다(2026-11-03 토스 회신).
   * 판정은 서버가 한다. 화면에서 다시 따지면 두 판정이 갈라진다.
   */
  cardPayable: boolean;
  /**
   * 배송비(원). 0 이면 무료.
   *
   * <p>「무료」를 화면에 글자로 박지 않는다. 박아 두면 배송비를 받기로 한 날
   * 설정만 바꿔서는 화면이 따라오지 않고, 장바구니는 무료라는데 주문서에서 돈이 붙는다.
   * 주문서와 같은 계산을 지난 값이다.
   */
  shippingFee: number;
  /** 결제 예정 금액 = 합계 + 배송비. 화면이 더하지 않는다 (원칙 P1) */
  payableTotal: number;
  notice?: string | null;
}
