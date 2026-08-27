/**
 * 관리자 API 타입. 백엔드 DTO 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 {@code non_null} 직렬화라 null 필드를 응답에서 생략한다.
 * {@code | null} 로 적힌 값은 런타임에 {@code undefined} 로 도착할 수 있으므로
 * 비교는 {@code == null} (느슨한 비교)로 한다.
 */

import type { MetalType, PriceSide, PricePolicyMode, ProductPriceType } from '@/types/price';
import type { ProductStatus } from '@/types/product';

// =====================================================================
// 인증 · 권한
// =====================================================================

/** 권한 등급. 상위가 하위를 포함한다 (SUPER_ADMIN > ADMIN > MANAGER > OPERATOR). */
export type AdminRole = 'SUPER_ADMIN' | 'ADMIN' | 'MANAGER' | 'OPERATOR';

export type AdminStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED';

export const ADMIN_ROLE_LABEL: Record<AdminRole, string> = {
  SUPER_ADMIN: '최고관리자',
  ADMIN: '관리자',
  MANAGER: '매니저',
  OPERATOR: '운영자',
};

export const ADMIN_STATUS_LABEL: Record<AdminStatus, string> = {
  ACTIVE: '정상',
  LOCKED: '잠김',
  DISABLED: '정지',
};

/** 권한 서열. 화면에서 "ADMIN 이상"을 판정할 때 쓴다. */
const ROLE_RANK: Record<AdminRole, number> = {
  OPERATOR: 1,
  MANAGER: 2,
  ADMIN: 3,
  SUPER_ADMIN: 4,
};

/**
 * 최소 등급 충족 여부.
 *
 * <p>이 판정은 <b>화면을 감추기 위한 것일 뿐</b>이다. 실제 차단은 백엔드
 * {@code @PreAuthorize} 가 한다 — 프론트엔드 검사만 믿으면 API 를 직접 부르는 순간 뚫린다.
 */
export function hasRole(role: AdminRole | undefined, minimum: AdminRole): boolean {
  if (!role) return false;
  return ROLE_RANK[role] >= ROLE_RANK[minimum];
}

export interface AdminUser {
  id: number;
  loginId: string;
  name: string;
  role: AdminRole;
  status: AdminStatus;
  loginFailCount: number;
  lastLoginAt?: string | null;
  lockedAt?: string | null;
  passwordChangedAt?: string | null;
  createdAt: string;
}

export interface AdminLoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** Access 토큰 만료까지 남은 초 */
  expiresIn: number;
  admin: AdminUser;
}

// =====================================================================
// 시세
// =====================================================================

export type PriceAvailability = 'AVAILABLE' | 'NONE' | 'STALE';

export interface AdminCurrentPrice {
  metalType: MetalType;
  metalName: string;
  priceSide: PriceSide;
  available: boolean;
  availability: PriceAvailability;
  /** 원본 시세 — 관리자 전용 */
  rawPricePerGram?: number | null;
  /** 보정액 — 관리자 전용 */
  adjustment?: number | null;
  pricePerGram?: number | null;
  policyMode?: PricePolicyMode | null;
  effectiveFrom?: string | null;
  changeReason?: string | null;
  createdBy?: string | null;
}

export interface PricePolicy {
  id: number;
  metalType: MetalType;
  priceSide: PriceSide;
  mode: PricePolicyMode;
  modeName: string;
  adjAmount: number;
  adjPercent: number;
  manualPrice?: number | null;
  updatedBy?: string | null;
  updatedAt: string;
}

export interface PriceApplyResult {
  /** false 면 오류가 아니라 <b>승인 대기로 등록됨</b>을 뜻한다 */
  applied: boolean;
  effectiveId?: number | null;
  pendingId?: number | null;
  rawPricePerGram: number;
  adjustment: number;
  pricePerGram: number;
  changePercent?: number | null;
  effectiveFrom?: string | null;
  message: string;
}

export interface PriceHistory {
  id: number;
  metalType: MetalType;
  priceSide: PriceSide;
  pricePerGram: number;
  baseRawId?: number | null;
  policyMode: PricePolicyMode;
  effectiveFrom: string;
  /** null 이면 현재 적용값 */
  effectiveTo?: string | null;
  current: boolean;
  changeReason: string;
  createdBy: string;
}

export type PendingStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface PricePending {
  id: number;
  rawId: number;
  metalType: MetalType;
  priceSide: PriceSide;
  /** 승인 시 적용될 값 */
  calcPrice: number;
  /** 보류 시점의 현재 시세 — 지금도 이 값이 적용 중이다 */
  prevPrice?: number | null;
  changePercent?: number | null;
  status: PendingStatus;
  decidedBy?: string | null;
  decidedAt?: string | null;
  createdAt: string;
}

export type FetchResult =
  | 'SUCCESS'
  | 'SKIPPED_DUPLICATE'
  | 'HTTP_ERROR'
  | 'TIMEOUT'
  | 'PARSE_ERROR'
  | 'INVALID_VALUE'
  | 'EXCEPTION';

export interface CollectionStatus {
  schedulerEnabled: boolean;
  cron: string;
  lastAttemptAt?: string | null;
  lastAttemptResult?: FetchResult | null;
  lastSuccessAt?: string | null;
  failureCount: number;
  neverSucceeded: boolean;
  message: string;
}

// =====================================================================
// 상품
// =====================================================================

export interface AdminProduct {
  id: number;
  categoryId: number;
  name: string;
  description?: string | null;
  thumbnailKey?: string | null;
  metalType?: MetalType | null;
  priceType: ProductPriceType;
  priceTypeName: string;
  weightG?: number | null;
  purity?: number | null;
  laborCost: number;
  multiplier: number;
  fixedPrice?: number | null;
  status: ProductStatus;
  active: boolean;
  createdBy?: string | null;
  createdAt: string;
  updatedBy?: string | null;
  updatedAt: string;
}

export interface AdminProductOption {
  id: number;
  name: string;
  weightG?: number | null;
  additionalLaborCost: number;
  /** 관리자 응답에만 담긴다 */
  stock?: number | null;
  inStock: boolean;
  sort: number;
  active: boolean;
}

/** 관리자 가격 미리보기 — 원본 시세·보정액·정책까지 전부 나온다. */
export interface AdminPriceQuote {
  status: 'AVAILABLE' | 'INQUIRY_ONLY' | 'SOLD_OUT' | 'NOT_FOR_SALE';
  orderable: boolean;
  productId: number;
  optionId?: number | null;
  appliedMarketPrice?: number | null;
  rawMarketPrice?: number | null;
  marketAdjustment?: number | null;
  effectivePriceId?: number | null;
  priceQuotedAt?: string | null;
  components: AdminPriceComponent[];
  subtotal?: number | null;
  vatMode?: string | null;
  vatPolicyTemporary: boolean;
  vat?: number | null;
  roundingPolicy?: string | null;
  roundingPolicyTemporary: boolean;
  roundingDelta?: number | null;
  finalUnitPrice?: number | null;
  unavailableReason?: string | null;
}

export interface AdminPriceComponent {
  sort: number;
  type: string;
  typeName: string;
  label: string;
  method: string;
  unitPricePerGram?: number | null;
  weightG?: number | null;
  rate?: number | null;
  amount: number;
}

// =====================================================================
// 감사로그
// =====================================================================

export type ActorType = 'ADMIN' | 'USER' | 'SYSTEM';

export interface AuditLog {
  id: number;
  actorType: ActorType;
  actor: string;
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  beforeValue?: string | null;
  afterValue?: string | null;
  ip?: string | null;
  userAgent?: string | null;
  createdAt: string;
}
