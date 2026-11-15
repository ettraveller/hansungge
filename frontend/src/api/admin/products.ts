import { adminApi } from '@/api/admin/client';
import type { AdminPriceQuote, AdminProduct, AdminProductOption } from '@/types/admin';
import type { MetalType, ProductPriceType } from '@/types/price';
import type { ProductDetail, ProductStatus } from '@/types/product';
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

/**
 * 이미지 갈래. 백엔드의 ProductImageKind 와 값이 같아야 한다.
 *
 * - `GALLERY` — 상세 상단 대표이미지. **첫 장이 목록·장바구니·주문서에 쓰인다.**
 * - `DETAIL` — 상품 설명 아래에 전체 폭으로 쌓는 상세페이지 이미지.
 */
export type ProductImageKind = 'GALLERY' | 'DETAIL';

export function listImages(id: number, kind: ProductImageKind) {
  return adminApi.get<string[]>(`/api/admin/products/${id}/images`, { query: { kind } });
}

/** 그 갈래만 교체한다. 다른 갈래는 손대지 않는다. */
export function replaceImages(id: number, kind: ProductImageKind, objectKeys: string[]) {
  return adminApi.put<string[]>(`/api/admin/products/${id}/images`, {
    query: { kind },
    body: objectKeys,
  });
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
  // ⚠️ thumbnailKey 는 없다. 대표이미지 목록(PUT /images?kind=GALLERY)의 첫 장이
  // 그 값이 되고, 서버가 맞춘다. 여기에 두면 상품 정보를 저장할 때마다 폼에 없는
  // 값이 null 로 덮어써서 목록의 사진이 사라진다.
  metalType?: MetalType | null;
  priceType: ProductPriceType;
  weightG?: string | null;
  /** 순도는 <b>표시 전용</b>이다. 계산에 곱하면 순도가 두 번 반영된다. */
  purity?: string | null;
  laborCost?: string | null;
  multiplier?: string | null;
  fixedPrice?: string | null;
  /**
   * 주문을 받아 개별 생산하는 상품인가 (V33).
   *
   * <p>상세의 구분 표시와 청약철회 제한 고지가 이 값으로 갈린다. 배송기간은
   * V41 부터 여기서 갈리지 않는다 -- deliveryPeriod 가 상품마다 받는다.
   * 보내지 않으면 서버가 false 로 읽는다 -- 빠뜨렸을 때 안전한 쪽이다.
   */
  madeToOrder: boolean;
  /**
   * 과세 상품인가 (V38).
   *
   * 과세여도 고객이 내는 금액은 바뀌지 않는다 - 판매가에 이미 부가세가
   * 들어 있다고 보고 공급가액과 세액을 쪼갤 뿐이다. 세율은 상품마다 두지 않고
   * 설정(VAT_RATE)을 쓴다.
   */
  vatTaxable: boolean;
  /**
   * 배송기간 문구 (V41). 비우면 상세가 「상품별 상이 · 상세페이지 참조」를 쓴다.
   *
   * ⚠️ 비워 두는 상품은 상세 이미지에 기간이 적혀 있어야 한다 — PG 심사가
   * 상품페이지 내 기재를 요구하고, 전자상거래법 §13② 의 공급시기 고지다.
   */
  deliveryPeriod?: string | null;
  /** 택배사 (V41). 비우면 「우체국택배」 */
  courier?: string | null;
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

/**
 * 등록 전 가격 미리보기 — 상품을 만들지 않고 입력값만으로 계산한다.
 *
 * <p>금액을 화면에서 계산하지 않는다. 시세·보정·부가세·반올림 규칙이 전부 서버에
 * 있고, 둘이 갈라지는 날 미리보기와 실제 판매가가 달라진다 (설계서 원칙 P1).
 *
 * <p>POST 지만 아무것도 저장하지 않는다 — 가격 재료가 일곱 개라 본문으로 받는다.
 */
export interface DraftPriceInput {
  priceType: ProductPriceType;
  metalType?: MetalType | null;
  weightG?: string | null;
  purity?: string | null;
  laborCost?: string | null;
  multiplier?: string | null;
  fixedPrice?: string | null;
  additionalLaborCost?: string | null;
}

export function previewDraftPrice(input: DraftPriceInput) {
  return adminApi.post<AdminPriceQuote>('/api/admin/products/price-preview', { body: input });
}

/** 상세 미리보기 입력. 가격 재료는 {@link DraftPriceInput} 를 그대로 품는다. */
export interface DraftDetailInput {
  name: string;
  description?: string | null;
  galleryKeys: string[];
  detailKeys: string[];
  madeToOrder: boolean;
  /**
   * 과세 상품인가 (V38).
   *
   * 과세여도 고객이 내는 금액은 바뀌지 않는다 - 판매가에 이미 부가세가
   * 들어 있다고 보고 공급가액과 세액을 쪼갤 뿐이다. 세율은 상품마다 두지 않고
   * 설정(VAT_RATE)을 쓴다.
   */
  vatTaxable: boolean;
  /** 배송기간 문구 (V41). 비우면 미리보기도 기본 문구로 보인다 */
  deliveryPeriod?: string | null;
  /** 택배사 (V41). 비우면 「우체국택배」 */
  courier?: string | null;
  pricing: DraftPriceInput;
}

/**
 * 등록 전 상세 미리보기.
 *
 * <p>공개 상세({@code GET /api/v1/products/{id}})와 <b>같은 모양</b>으로 돌아온다.
 * 관리자 화면이 그 응답으로 고객용 상세 컴포넌트를 그대로 그린다 — 비슷하게 다시
 * 그리면 그쪽이 바뀔 때 미리보기만 낡아서 "미리보기와 실제가 다르다"가 된다.
 */
export function previewDraftDetail(input: DraftDetailInput) {
  return adminApi.post<ProductDetail>('/api/admin/products/detail-preview', { body: input });
}

/**
 * 진열 순서 저장.
 *
 * <p>보낸 차례가 그대로 쇼핑몰 목록 순서가 된다. 「골드바」 탭에서 끌었더라도
 * 화면이 들고 있는 <b>전체</b> 순서를 보낸다 — 일부만 보내면 보내지 않은 상품과
 * 자리 번호가 겹쳐, 끌어다 놓은 자리와 다른 곳에 선다.
 */
export function reorderProducts(productIds: number[]) {
  return adminApi.put<void>('/api/admin/products/order', { body: { productIds } });
}
