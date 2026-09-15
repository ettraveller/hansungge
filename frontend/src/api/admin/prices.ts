import { adminApi } from '@/api/admin/client';
import type {
  AdminCurrentPrice,
  DerivationUpdateResult,
  PriceDerivation,
  PriceHistory,
  QuotePlan,
  QuotePublishResult,
} from '@/types/admin';
import type { MetalType, PricePolicyMode, PriceSide } from '@/types/price';
import type { PageResponse } from '@/types/api';

export function currentPrices() {
  return adminApi.get<AdminCurrentPrice[]>('/api/admin/prices/current');
}

export function priceHistory(metal: MetalType, side: PriceSide, page = 0, size = 20) {
  return adminApi.get<PageResponse<PriceHistory>>('/api/admin/prices/history', {
    query: { metal, side, page, size },
  });
}

/** 18K·14K 가 직접 입력인지 24K 환산인지. */
export function derivations() {
  return adminApi.get<PriceDerivation[]>('/api/admin/prices/derivations');
}

export interface PairPreviewInput {
  metalType: MetalType;
  /** 입력한 값이 곧 고시가다 (KRW/1g). 금액은 문자열로 보낸다 — 부동소수 오차를 타지 않게. */
  sellPricePerGram: string;
  buyPricePerGram: string;
}

export interface DeclarePairInput extends PairPreviewInput {
  changeReason: string;
  /** 미리보기에서 needsConfirm 이었던 것을 관리자가 확인했는가 */
  confirmLargeChange?: boolean;
}

/**
 * 고시 미리보기 — 아무것도 바꾸지 않는다.
 *
 * <p>24K 면 환산으로 설정된 18K·14K 가 얼마로 바뀌는지도 함께 온다. 값이 검증에 걸리면
 * 고시와 똑같은 400 이 온다.
 */
export function previewPair(input: PairPreviewInput) {
  return adminApi.post<QuotePlan>('/api/admin/prices/apply-pair/preview', { body: input });
}

/**
 * 살 때·팔 때를 한 번에 고시한다.
 *
 * <p>확인 기준을 넘는 값이 있는데 {@code confirmLargeChange} 없이 보내면 409 이고
 * 아무것도 저장되지 않는다.
 */
export function declarePair(input: DeclarePairInput) {
  return adminApi.post<QuotePublishResult>('/api/admin/prices/apply-pair', { body: input });
}

export interface DerivationPreviewInput {
  metalType: MetalType;
  ratio: string;
}

export interface DerivationUpdateInput {
  metalType: MetalType;
  mode: Extract<PricePolicyMode, 'DIRECT' | 'DERIVED'>;
  /** DERIVED 일 때만 */
  ratio?: string | null;
  changeReason: string;
  confirmLargeChange?: boolean;
}

/** 이 비율이면 18K·14K 가 지금 24K 기준으로 얼마가 되는지. */
export function previewDerivation(input: DerivationPreviewInput) {
  return adminApi.post<QuotePlan>('/api/admin/prices/derivations/preview', { body: input });
}

/**
 * 고시 방식 변경. 환산으로 켜거나 비율을 고치면 서버가 곧바로 다시 고시한다.
 * 직접 입력으로 돌리면 마지막 값이 그대로 남는다.
 */
export function updateDerivation(input: DerivationUpdateInput) {
  return adminApi.put<DerivationUpdateResult>('/api/admin/prices/derivations', { body: input });
}
