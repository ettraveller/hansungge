/**
 * 관리자 API 타입. 백엔드 DTO 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 {@code non_null} 직렬화라 null 필드를 응답에서 생략한다.
 * {@code | null} 로 적힌 값은 런타임에 {@code undefined} 로 도착할 수 있으므로
 * 비교는 {@code == null} (느슨한 비교)로 한다.
 */

import type {
  MetalType,
  PriceChange,
  PriceSide,
  PricePolicyMode,
  ProductPriceType,
} from '@/types/price';
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

/** AVAILABLE = 고시되어 있다 · NONE = 한 번도 고시된 적 없다 */
export type PriceAvailability = 'AVAILABLE' | 'NONE';

export interface AdminCurrentPrice {
  metalType: MetalType;
  metalName: string;
  priceSide: PriceSide;
  available: boolean;
  availability: PriceAvailability;
  pricePerGram?: number | null;
  /** 직전 고시 대비. 처음 고시면 생략된다 */
  change?: PriceChange | null;
  /** 이 값이 나온 방식. 옛 행이면 V19 이전 값(FIXED_ADJ 등)일 수 있다 */
  mode?: PricePolicyMode | null;
  effectiveFrom?: string | null;
  changeReason?: string | null;
  createdBy?: string | null;
}

/** 18K·14K 한 금속의 고시 방식 */
export interface PriceDerivation {
  metalType: MetalType;
  metalName: string;
  mode: PricePolicyMode;
  modeName: string;
  /** 환산 비율. 직접 입력이어도 직전 비율이 남아 있을 수 있다 */
  ratio?: number | null;
  updatedBy?: string | null;
  updatedAt: string;
}

/** 고시로 바뀌는 한 줄. 미리보기와 발행 결과가 같은 모양이다 */
export interface QuoteLine {
  metalType: MetalType;
  metalName: string;
  priceSide: PriceSide;
  mode: PricePolicyMode;
  deriveRatio?: number | null;
  basePricePerGram?: number | null;
  /** 고시 전 값. 처음 고시면 생략된다 */
  currentPricePerGram?: number | null;
  newPricePerGram: number;
  changePercent?: number | null;
  exceedsLimit: boolean;
}

/** 고시 미리보기. needsConfirm 이면 확인을 받은 뒤 confirmLargeChange 로 다시 보낸다 */
export interface QuotePlan {
  needsConfirm: boolean;
  limitPercent: number;
  lines: QuoteLine[];
}

export interface QuotePublishResult {
  effectiveFrom: string;
  lines: QuoteLine[];
}

export interface DerivationUpdateResult {
  derivation: PriceDerivation;
  /** 곧바로 다시 고시된 결과. 24K 가 없거나 직접 입력으로 돌렸거나 값이 그대로면 생략된다 */
  published?: QuotePublishResult | null;
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

/** 관리자 가격 미리보기 — 적용 시세·구성요소·세금·반올림까지 전부 나온다. */
export interface AdminPriceQuote {
  status: 'AVAILABLE' | 'INQUIRY_ONLY' | 'SOLD_OUT' | 'NOT_FOR_SALE';
  orderable: boolean;
  productId: number;
  optionId?: number | null;
  appliedMarketPrice?: number | null;
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
