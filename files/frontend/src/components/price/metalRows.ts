import { METAL_ORDER, type MarketPrice, type MetalType, type PriceHistoryPoint } from '@/types/price';

/**
 * 시세 보드가 그리는 한 금속의 묶음.
 *
 * <p>이 파일에 {@code 'use client'} 가 <b>없는 것이 의도</b>다. 서버 컴포넌트가
 * {@link toMetalRows} 를 호출해 결과를 props 로 넘긴다. 클라이언트 모듈에 두면
 * "서버에서 클라이언트 함수를 호출할 수 없다"로 페이지가 통째로 죽는다.
 */
export interface MetalRow {
  metalType: MetalType;
  /** 판매 시세 — 고객이 살 때 */
  sell: MarketPrice | null;
  /** 매입 시세 — 고객이 팔 때 */
  buy: MarketPrice | null;
  /** 판매 시세의 추이. 고시 이력이 없으면 빈 배열 */
  history: PriceHistoryPoint[];
}

/**
 * 시세 배열 → 금속별 행.
 *
 * <p>금속 순서는 {@link METAL_ORDER} 를 따른다 (24K 가 맨 앞). 응답 순서에 기대지 않는
 * 이유는, 백엔드가 정렬을 바꿔도 화면 순서가 흔들리면 안 되기 때문이다.
 *
 * <p>없는 조합은 {@code null} 로 남긴다 — 화면이 "시세문의"로 그린다.
 */
export function toMetalRows(
  prices: MarketPrice[],
  histories: Partial<Record<MetalType, PriceHistoryPoint[]>>,
): MetalRow[] {
  return METAL_ORDER.map((metalType) => ({
    metalType,
    sell: prices.find((p) => p.metalType === metalType && p.priceSide === 'SELL') ?? null,
    buy: prices.find((p) => p.metalType === metalType && p.priceSide === 'BUY') ?? null,
    history: histories[metalType] ?? [],
  }));
}
