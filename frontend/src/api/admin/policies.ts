import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';

/**
 * 환불규정의 한 판 (관리자).
 *
 * <p>개정은 기존 판을 고치는 게 아니라 <b>새 판을 쌓는 것</b>이다. 지난 판은 이미
 * 만들어진 주문들이 "동의한 그 내용"이라 서버·DB 가 수정을 막는다.
 */
export interface RefundPolicyVersion {
  /** 판 번호. 클수록 최신이다 */
  id: number;
  content: string;
  changeNote: string;
  createdBy: string;
  createdAt: string;
}

export function getRefundPolicy() {
  return adminApi.get<RefundPolicyVersion>('/api/admin/policies/refund');
}

export function getRefundPolicyHistory(page = 0, size = 20) {
  return adminApi.get<PageResponse<RefundPolicyVersion>>('/api/admin/policies/refund/history', {
    query: { page, size },
  });
}

/** 저장하면 쇼핑몰과 주문서에 바로 보인다. 직전 판과 내용이 같으면 서버가 거절한다. */
export function publishRefundPolicy(content: string, changeNote: string) {
  return adminApi.post<RefundPolicyVersion>('/api/admin/policies/refund', {
    body: { content, changeNote },
  });
}
