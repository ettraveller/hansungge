import { adminApi } from '@/api/admin/client';
import type { AdminPriceQuote, AdminProduct, AdminProductOption } from '@/types/admin';
import type { MetalType, ProductPriceType } from '@/types/price';
import type { ProductStatus } from '@/types/product';
import type { PageResponse } from '@/types/api';

export interface ProductSearch {
  categoryId?: number | null;
  keyword?: string | null;
  metalType?: MetalType | null;
  priceType?: ProductPriceType | null;
  status?: ProductStatus | null;
  active?: boolean | null;
  page?: number;
  size?: number;
}

/** 관리자 목록은 숨김·판매종료·비활성 상품까지 전부 조회된다. */
export function listProducts(search: ProductSearch = {}) {
  return adminApi.get<PageResponse<AdminProduct>>('/api/admin/products', {
    query: {
      categoryId: search.categoryId,
      keyword: search.keyword,
      metalType: search.metalType,
      priceType: search.priceType,
      status: search.status,
      active: search.active,
      page: search.page ?? 0,
      size: search.size ?? 20,
    },
  });
}

export function getProduct(id: number) {
  return adminApi.get<AdminProduct>(`/api/admin/products/${id}`);
}

export function listOptions(id: number) {
  return adminApi.get<AdminProductOption[]>(`/api/admin/products/${id}/options`);
}

export function listImages(id: number) {
  return adminApi.get<string[]>(`/api/admin/products/${id}/images`);
}

export function replaceImages(id: number, objectKeys: string[]) {
  return adminApi.put<string[]>(`/api/admin/products/${id}/images`, { body: objectKeys });
}

/**
 * 상품 등록/수정 입력.
 *
 * <b>판매가는 없다.</b> 시세 연동 상품의 가격은 시세 × 중량 + 공임으로 서버가 계산한다
 * (설계서 원칙 P1). FIXED/MANUAL 일 때만 {@link fixedPrice} 를 쓴다.
 *
 * 금액·중량은 문자열로 보낸다 — number 로 만들면 자바스크립트가 소수를 흘린다.
 */
export interface ProductInput {
  categoryId: number;
  name: string;
  description?: string | null;
  thumbnailKey?: string | null;
  metalType?: MetalType | null;
  priceType: ProductPriceType;
  weightG?: string | null;
  /** 순도는 <b>표시 전용</b>이다. 계산에 곱하면 순도가 두 번 반영된다. */
  purity?: string | null;
  laborCost?: string | null;
  multiplier?: string | null;
  fixedPrice?: string | null;
  /** 등록 시에만 쓰인다 */
  defaultOptionName?: string | null;
  initialStock?: number | null;
}

/** HIDDEN 상태로 생성되고, 재고를 담을 기본 옵션 1개가 자동 생성된다. */
export function createProduct(input: ProductInput) {
  return adminApi.post<AdminProduct>('/api/admin/products', { body: input });
}

export function updateProduct(id: number, input: ProductInput) {
  return adminApi.put<AdminProduct>(`/api/admin/products/${id}`, { body: input });
}

/** ON_SALE 로 바꾸려면 활성 옵션이 최소 1개 있어야 한다. 사유는 필수다. */
export function changeStatus(id: number, status: ProductStatus, active: boolean, reason: string) {
  return adminApi.patch<AdminProduct>(`/api/admin/products/${id}/status`, {
    body: { status, active, reason },
  });
}

export interface OptionInput {
  name: string;
  weightG?: string | null;
  additionalLaborCost?: string | null;
  /** 등록 시에만 쓰인다. 수정은 재고 조정 API 로 한다. */
  stock?: number | null;
  sort: number;
  active?: boolean;
}

export function addOption(productId: number, input: OptionInput) {
  return adminApi.post<AdminProductOption>(`/api/admin/products/${productId}/options`, {
    body: input,
  });
}

export function updateOption(productId: number, optionId: number, input: OptionInput) {
  return adminApi.put<AdminProductOption>(
    `/api/admin/products/${productId}/options/${optionId}`,
    { body: input },
  );
}

/**
 * 재고 조정.
 *
 * 증감이 아니라 <b>절대 수량</b>이다 — 요청이 중복 도착해도 재고가 두 번 깎이지 않는다.
 */
export function adjustStock(productId: number, optionId: number, stock: number, reason: string) {
  return adminApi.patch<AdminProductOption>(
    `/api/admin/products/${productId}/options/${optionId}/stock`,
    { body: { stock, reason } },
  );
}

/** 원본 시세·보정액까지 보이는 관리자 전용 가격 미리보기. */
export function previewPrice(productId: number, optionId?: number | null) {
  return adminApi.get<AdminPriceQuote>(`/api/admin/products/${productId}/price-preview`, {
    query: { optionId },
  });
}

export function previewAllOptions(productId: number) {
  return adminApi.get<AdminPriceQuote[]>(
    `/api/admin/products/${productId}/price-preview/options`,
  );
}
