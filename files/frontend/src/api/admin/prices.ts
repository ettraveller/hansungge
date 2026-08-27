import { adminApi } from '@/api/admin/client';
import type {
  AdminCurrentPrice,
  CollectionStatus,
  PendingStatus,
  PriceApplyResult,
  PriceHistory,
  PricePending,
  PricePolicy,
} from '@/types/admin';
import type { MetalType, PricePolicyMode, PriceSide } from '@/types/price';
import type { PageResponse } from '@/types/api';

export function currentPrices() {
  return adminApi.get<AdminCurrentPrice[]>('/api/admin/prices/current');
}

export function collectionStatus() {
  return adminApi.get<CollectionStatus>('/api/admin/prices/collection-status');
}

export function policies() {
  return adminApi.get<PricePolicy[]>('/api/admin/prices/policies');
}

export function priceHistory(metal: MetalType, side: PriceSide, page = 0, size = 20) {
  return adminApi.get<PageResponse<PriceHistory>>('/api/admin/prices/history', {
    query: { metal, side, page, size },
  });
}

export function pendingPrices(status: PendingStatus = 'PENDING', page = 0, size = 20) {
  return adminApi.get<PageResponse<PricePending>>('/api/admin/prices/pending', {
    query: { status, page, size },
  });
}

export interface DeclareInput {
  metalType: MetalType;
  priceSide: PriceSide;
  /** 최종가가 아니라 <b>원본 시세(KRW/1g)</b>다. 보정은 서버가 정책대로 적용한다. */
  pricePerGram: string;
  changeReason: string;
}

/**
 * 수동 고시.
 *
 * ⚠️ 실패가 아닌데 {@code applied: false} 로 돌아올 수 있다 — 변동률이 임계값을 넘어
 * 승인 대기로 등록된 경우다. 이때 현재 시세는 바뀌지 않는다. 화면은 이 경우를
 * "성공"으로도 "실패"로도 표시하면 안 된다.
 */
export function declarePrice(input: DeclareInput) {
  return adminApi.post<PriceApplyResult>('/api/admin/prices/apply', { body: input });
}

export interface PolicyInput {
  metalType: MetalType;
  priceSide: PriceSide;
  mode: PricePolicyMode;
  adjAmount?: string | null;
  adjPercent?: string | null;
  manualPrice?: string | null;
  changeReason: string;
}

/** 이미 발행된 시세는 바뀌지 않는다. 다음 고시부터 반영된다. */
export function updatePolicy(input: PolicyInput) {
  return adminApi.put<PricePolicy>('/api/admin/prices/policies', { body: input });
}

export function approvePending(id: number, reason: string) {
  return adminApi.post<PriceApplyResult>(`/api/admin/prices/pending/${id}/approve`, {
    body: { reason },
  });
}

export function rejectPending(id: number, reason: string) {
  return adminApi.post<PricePending>(`/api/admin/prices/pending/${id}/reject`, {
    body: { reason },
  });
}
