/**
 * 상품 도메인 타입. 백엔드 DTO 와 1:1 대응한다.
 *   CategoryResponse       → Category
 *   ProductSummaryResponse → ProductSummary
 *   ProductDetailResponse  → ProductDetail
 *   ProductOptionResponse  → ProductOption
 */

import type { MetalType, ProductPrice } from '@/types/price';

export type ProductStatus = 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN' | 'DISCONTINUED';

export const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = {
  ON_SALE: '판매중',
  SOLD_OUT: '품절',
  HIDDEN: '숨김',
  DISCONTINUED: '판매종료',
};

/**
 * ⚠️ 백엔드는 {@code spring.jackson.default-property-inclusion: non_null} 이라
 * <b>null 필드를 응답에서 아예 생략한다.</b> 즉 여기 {@code | null} 로 적힌 값은
 * 런타임에 {@code undefined} 로 도착할 수 있다.
 *
 * 그래서 비교는 반드시 {@code == null} / {@code != null}(느슨한 비교)로 한다 —
 * {@code === null} 은 생략된 필드를 놓친다. 타입에도 {@code ?} 를 붙여 그 사실을 드러낸다.
 */
export interface Category {
  id: number;
  parentId?: number | null;
  name: string;
  sort: number;
  active: boolean;
  updatedAt: string;
}

/**
 * 상품 옵션.
 *
 * 공개 응답에서 {@link stock} 은 항상 null 이다 — 정확한 재고는 영업 정보이고,
 * 고객이 알아야 할 것은 {@link inStock}("살 수 있는가") 뿐이다.
 */
export interface ProductOption {
  id: number;
  name: string;
  weightG?: number | null;
  additionalLaborCost: number;
  stock?: number | null;
  inStock: boolean;
  sort: number;
  active: boolean;
}

/**
 * 목록 항목.
 *
 * {@link price} 는 <b>대표가(최저 옵션가)</b>이고, 옵션이 여럿이면 {@link priceFrom} 이
 * true 라 "○○원~" 으로 표시한다. 표시 불가면 price 가 null 이고 priceText 가 "시세문의"다.
 */
export interface ProductSummary {
  id: number;
  categoryId: number;
  name: string;
  thumbnailKey?: string | null;
  metalType?: MetalType | null;
  weightG?: number | null;
  status: ProductStatus;
  price?: number | null;
  priceText: string;
  priceFrom: boolean;
  orderable: boolean;
}

/**
 * 상세.
 *
 * {@link options} 와 {@link prices} 는 <b>같은 순서</b>다. 가격이 이미 계산되어 오므로
 * 프론트엔드가 시세×중량을 다시 계산할 일이 없다 (설계서 원칙 P1).
 */
export interface ProductDetail {
  id: number;
  categoryId: number;
  name: string;
  description?: string | null;
  thumbnailKey?: string | null;
  imageKeys: string[];
  metalType?: MetalType | null;
  weightG?: number | null;
  purity?: number | null;
  status: ProductStatus;
  options: ProductOption[];
  prices: ProductPrice[];
}
