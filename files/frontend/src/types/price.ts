/**
 * 시세/가격 도메인 타입.
 *
 * 백엔드 DTO 와 1:1 대응한다 — 이름이나 값이 어긋나면 화면이 조용히 빈 값을 그린다.
 *   PublicPriceResponse       → MarketPrice
 *   PublicPriceQuoteResponse  → ProductPrice
 *   ComponentLineResponse     → PriceComponent
 *
 * 금액은 JSON number 로 온다 (백엔드 BigDecimal → Jackson). 프론트엔드는 이 값을
 * <b>표시만</b> 하고 더하거나 곱하지 않는다 (설계서 원칙 P1).
 */

export type MetalType = 'GOLD_24K' | 'GOLD_18K' | 'GOLD_14K' | 'SILVER' | 'PLATINUM';

export type PriceSide = 'SELL' | 'BUY';

export type PricePolicyMode = 'AUTO' | 'FIXED_ADJ' | 'PERCENT_ADJ' | 'MANUAL';

export type ProductPriceType =
  | 'FIXED'
  | 'MARKET_LINKED'
  | 'MARKET_PLUS'
  | 'MARKET_MULTIPLIER'
  | 'MANUAL';

/**
 * 가격 표시 가능 여부.
 *  AVAILABLE     정상
 *  INQUIRY_ONLY  시세 없음/노후 → "시세문의", 주문 차단
 *  SOLD_OUT      품절 (가격은 표시하되 주문만 차단)
 *  NOT_FOR_SALE  비노출 상품
 */
export type PriceStatus = 'AVAILABLE' | 'INQUIRY_ONLY' | 'SOLD_OUT' | 'NOT_FOR_SALE';

export const METAL_LABEL: Record<MetalType, string> = {
  GOLD_24K: '순금 24K',
  GOLD_18K: '18K',
  GOLD_14K: '14K',
  SILVER: '은',
  PLATINUM: '백금',
};

/** 시세표에 이 순서로 노출한다. 24K 가 주력이라 맨 앞이다. */
export const METAL_ORDER: MetalType[] = ['GOLD_24K', 'GOLD_18K', 'GOLD_14K', 'SILVER', 'PLATINUM'];

/**
 * 공개 시세 한 줄 (`GET /api/v1/prices/current`).
 *
 * 사용할 수 없는 시세는 <b>금액이 없고</b> {@link displayText} 가 "시세문의"다.
 * 0 이 오는 경로는 백엔드에 존재하지 않는다.
 *
 * ⚠️ 백엔드는 {@code spring.jackson.default-property-inclusion: non_null} 이라
 * <b>null 필드를 응답에서 아예 생략한다.</b> 즉 여기 {@code | null} 로 적힌 값은
 * 런타임에 {@code undefined} 로 도착할 수 있다.
 *
 * 그래서 비교는 반드시 {@code == null} / {@code != null}(느슨한 비교)로 한다 —
 * {@code === null} 은 생략된 필드를 놓친다. 타입에도 {@code ?} 를 붙여 그 사실을 드러낸다.
 */
export interface MarketPrice {
  metalType: MetalType;
  metalName: string;
  priceSide: PriceSide;
  sideName: string;
  available: boolean;
  /** 1g 시세(원). 사용 불가면 null */
  pricePerGram?: number | null;
  /** 1돈(3.75g) 시세(원). 사용 불가면 null */
  pricePerDon?: number | null;
  /** 화면 표시 문구. 사용 불가면 "시세문의" */
  displayText: string;
  effectiveFrom?: string | null;
}

/** 가격 내역 한 줄. 합계가 정확히 소계와 같다 (백엔드 불변식). */
export interface PriceComponent {
  sort: number;
  type: 'METAL_BASE' | 'LABOR' | 'MATERIAL' | 'STONE' | 'MARGIN' | 'PACKAGING' | 'DISCOUNT';
  typeName: string;
  label: string;
  method: string;
  /** PER_GRAM_* 방식일 때의 단가 */
  unitPricePerGram?: number | null;
  weightG?: number | null;
  rate?: number | null;
  amount: number;
}

/** 상품 가격 (`GET /api/v1/products/{id}/price`). */
export interface ProductPrice {
  productId: number;
  optionId?: number | null;
  status: PriceStatus;
  orderable: boolean;
  /** 최종 판매 단가(원). 표시 불가면 null */
  price?: number | null;
  displayText: string;
  components: PriceComponent[];
  marketPricePerGram?: number | null;
  priceQuotedAt?: string | null;
  notice?: string | null;
}
