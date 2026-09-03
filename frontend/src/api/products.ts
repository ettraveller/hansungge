import { api } from '@/api/client';
import type { PageResponse } from '@/types/api';
import type { Category, ProductDetail, ProductSummary } from '@/types/product';

export interface ProductListQuery {
  categoryId?: number;
  keyword?: string;
  page?: number;
  size?: number;
}

/**
 * 상품 API.
 *
 * 목록·상세에 <b>가격이 이미 계산되어</b> 담겨 온다. 프론트엔드가 시세×중량을 다시
 * 계산하지 않는 이유는 설계서 원칙 P1 — 계산하는 곳이 둘이 되는 순간 두 값이 어긋난다.
 *
 * 상품 정보 자체는 자주 바뀌지 않지만 <b>가격이 함께 실려 오므로</b> 캐시하지 않는다.
 */
export const productApi = {
  categories: () => api.get<Category[]>('/api/v1/categories', { cache: 'no-store' }),

  list: (query: ProductListQuery = {}) =>
    api.get<PageResponse<ProductSummary>>('/api/v1/products', {
      query: {
        categoryId: query.categoryId,
        keyword: query.keyword,
        page: query.page,
        size: query.size,
      },
      cache: 'no-store',
    }),

  detail: (productId: number) =>
    api.get<ProductDetail>(`/api/v1/products/${productId}`, { cache: 'no-store' }),
};
