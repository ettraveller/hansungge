import { adminApi } from '@/api/admin/client';
import type {
  AdminCurrentPrice,
  CollectionStatus,
  PendingStatus,
  PriceApplyPairResult,
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

export interface DeclarePairInput {
  metalType: MetalType;
  sellPricePerGram: string;
  buyPricePerGram: string;
  changeReason: string;
}

/**
 * 살 때·팔 때를 한 번에 고시한다.
 *
 * <p>한쪽씩 고시하는 경로를 화면에 두지 않는다 — 그 사이에 한쪽만 값이 있는
 * 시세표가 고객에게 걸린다.
 */
export function declarePair(input: DeclarePairInput) {
  return adminApi.post<PriceApplyPairResult>('/api/admin/prices/apply-pair', { body: input });
}

/**
 * 바뀐 보정값을 지금 시세에 다시 적용한다.
 *
 * <p>보정값을 바꿔도 이미 발행된 시세는 바뀌지 않는다(append-only). 새 보정은
 * 다음 고시부터 적용되는데, 다음 고시가 언제 올지 모르면 "고쳐도 아무 일도 안
 * 일어난다"로 보인다. 이 호출이 가장 최근 원본에 새 보정을 적용해 새로 발행한다.
 *
 * <p>원본이 노후 한계보다 오래되었으면 서버가 거절한다.
 */
export function reapplyPolicy(metalType: MetalType, reason: string) {
  return adminApi.post<PriceApplyPairResult>(
    `/api/admin/prices/reapply?metal=${metalType}`,
    { body: { reason } },
  );
}

export interface AutoQuoteStartInput {
  metalType: MetalType;
  mode: PricePolicyMode;
  sellAdj: string;
  buyAdj: string;
  changeReason: string;
}

/**
 * 자동고시 시작 — <b>보정값과 함께</b> 켠다.
 *
 * <p>값을 정하지 않은 채 자동이 켜지는 경로를 두지 않는다. 보정 0 으로 켜지면
 * 수집값이 그대로 판매가가 되고, 마진 없이 파는 상태는 판 뒤에야 드러난다.
 */
export function startAutoQuote(input: AutoQuoteStartInput) {
  return adminApi.post<PricePolicy[]>('/api/admin/prices/policies/auto-quote', { body: input });
}

export interface QuoteModeInput {
  metalType: MetalType;
  autoQuote: boolean;
  changeReason: string;
}

/**
 * 자동고시 / 수동고시 전환 — <b>금속 단위</b>다.
 *
 * <p>살 때만 자동으로 둘 수 없다. 금값이 움직이면 두 값이 같이 움직여야 하는데,
 * 한쪽만 자동이면 그쪽만 움직여 시세표가 어긋난다.
 *
 * <p>보정값을 함께 보내지 않는다 — 화면에 남아 있던 옛 값이 딸려 들어가는 사고를 막는다.
 * 자동으로 켜도 변동률 임계를 넘는 값은 양쪽 모두 발행되지 않고 승인 대기로 간다.
 */
export function switchQuoteMode(input: QuoteModeInput) {
  return adminApi.put<PricePolicy[]>('/api/admin/prices/policies/quote-mode', { body: input });
}

/**
 * 승인 대기 여러 건을 한 번에 승인한다 — 보통 한 금속의 살 때·팔 때 두 건이다.
 *
 * <p>한 건씩 승인하면 그 사이에 한쪽만 새 값인 시세표가 화면에 걸린다.
 * 서버가 한 트랜잭션에서 처리하므로 하나라도 실패하면 전부 되돌아간다.
 */
export function approvePendingBatch(input: { ids: number[]; reason: string }) {
  return adminApi.post<PriceApplyResult[]>('/api/admin/prices/pending/approve', { body: input });
}

export function rejectPendingBatch(input: { ids: number[]; reason: string }) {
  return adminApi.post<PricePending[]>('/api/admin/prices/pending/reject', { body: input });
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
