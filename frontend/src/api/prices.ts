import { api } from '@/api/client';
import type {
  MarketPrice,
  MetalType,
  PriceHistoryPoint,
  PriceSide,
  ProductPrice,
} from '@/types/price';

/**
 * 시세 API.
 *
 * 전부 `no-store` 다. 시세는 이 서비스에서 <b>가장 자주 바뀌고 가장 틀리면 안 되는 값</b>이라
 * 응답을 캐시하지 않는다. 백엔드가 이미 60초 캐시 + 시세변경 이벤트 무효화를 하고 있어
 * 여기서 또 캐시하면 무효화 지점만 둘로 늘어난다.
 */
export const priceApi = {
  /** 전 금속 × 매입/판매 현재 시세. 사용 불가 조합도 "시세문의"로 포함된다. */
  current: () => api.get<MarketPrice[]>('/api/v1/prices/current', { cache: 'no-store' }),

  /**
   * 시세 이력 (차트용). 오래된 순으로 온다.
   *
   * 이 테이블이 append-only 라 <b>고시가 일어난 시점마다 한 행</b>이다. 하루에 여러 번
   * 고시하면 그날 점이 여러 개고, 고시가 없던 날은 점이 없다 — 일별 종가가 아니다.
   *
   * 기간이 시작할 때 적용 중이던 값이 <b>맨 앞에</b> 온다. 그 점의 시각은 기간보다 앞선다.
   */
  history: (metal: MetalType, days = 30, side: PriceSide = 'SELL') =>
    api.get<PriceHistoryPoint[]>('/api/v1/prices/history', {
      query: { metal, side, days },
      cache: 'no-store',
    }),

  /**
   * 시세 버전. 새로 고시되면 달라지는 숫자 하나다.
   *
   * 열어 둔 화면이 "시세가 바뀌었나"만 묻는 데 쓴다. 값의 뜻을 해석하지 말고 지난번과
   * 같은지만 비교한다.
   */
  version: () => api.get<{ version: number }>('/api/v1/prices/version', { cache: 'no-store' }),

  /** 상품 가격. optionId 를 주지 않으면 상품 대표 산출이다. */
  forProduct: (productId: number, optionId?: number) =>
    api.get<ProductPrice>(`/api/v1/products/${productId}/price`, {
      query: { optionId },
      cache: 'no-store',
    }),

  /** 옵션별 가격 (상세 화면). */
  forProductOptions: (productId: number) =>
    api.get<ProductPrice[]>(`/api/v1/products/${productId}/prices`, { cache: 'no-store' }),
};

/** 시세표에서 판매가만 골라낸다. 매입가는 게시용이라 상품 가격과 무관하다. */
export function sellPrices(prices: MarketPrice[]): MarketPrice[] {
  return prices.filter((p) => p.priceSide === 'SELL');
}

export function findPrice(
  prices: MarketPrice[],
  metalType: MetalType,
  side: PriceSide,
): MarketPrice | undefined {
  return prices.find((p) => p.metalType === metalType && p.priceSide === side);
}
