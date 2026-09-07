/**
 * 회원 (관리자 화면).
 *
 * 백엔드 {@code domain/user/dto/AdminMember*Response} 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 `non_null` 직렬화라 null 필드를 응답에서 생략한다.
 * `| null` 로 적힌 값은 런타임에 `undefined` 로 도착하므로 비교는 `== null` 로 한다.
 */

import type { OAuthProviderType } from '@/types/user';

export type MemberStatus = 'ACTIVE' | 'DORMANT' | 'WITHDRAWN' | 'BLOCKED';

export const MEMBER_STATUS_LABEL: Record<MemberStatus, string> = {
  ACTIVE: '정상',
  DORMANT: '휴면',
  WITHDRAWN: '탈퇴',
  BLOCKED: '이용정지',
};

/** 필터 셀렉트와 요약 줄의 순서. 자주 쓰는 것부터. */
export const MEMBER_STATUS_ORDER: MemberStatus[] = ['ACTIVE', 'BLOCKED', 'DORMANT', 'WITHDRAWN'];

/**
 * 관리자가 지정할 수 있는 상태.
 *
 * <p>`WITHDRAWN` 이 없다. 탈퇴는 본인의 의사표시라 관리자가 대신 하지 않고, 되돌리지도
 * 않는다 — 서버도 거절한다.
 */
export const MEMBER_ASSIGNABLE_STATUS: MemberStatus[] = ['ACTIVE', 'BLOCKED', 'DORMANT'];

/**
 * 목록 한 줄.
 *
 * <p>`email`·`name`·`phone` 은 <b>이미 가려진 값</b>이다(`ho***@example.com`).
 * 화면에서 다시 자르지 않는다 — 서버가 원본을 보내지 않는다.
 */
export interface AdminMemberSummary {
  id: number;
  email: string;
  name: string;
  phone: string;
  status: MemberStatus;
  statusLabel: string;
  hasPassword: boolean;
  identityVerified: boolean;
  /** 연속 로그인 실패로 걸린 잠금. 상태(status)와는 별개다 */
  locked: boolean;
  loginFailCount: number;
  createdAt: string;
}

/** 상세 — 여기서만 원본 값이 온다. 조회 사실이 감사로그에 남는다. */
export interface AdminMemberDetail {
  id: number;
  email: string;
  name: string;
  phone?: string | null;
  status: MemberStatus;
  statusLabel: string;
  birthDate?: string | null;
  gender?: 'MALE' | 'FEMALE' | null;
  foreigner?: boolean | null;
  hasPassword: boolean;
  identityVerified: boolean;
  emailVerifiedAt?: string | null;
  phoneVerifiedAt?: string | null;
  passwordChangedAt?: string | null;
  loginFailCount: number;
  lockedAt?: string | null;
  locked: boolean;
  socialProviders: OAuthProviderType[];
  createdAt: string;
  updatedAt: string;
}

export interface MemberSearch {
  keyword?: string;
  status?: MemberStatus;
  page?: number;
}
