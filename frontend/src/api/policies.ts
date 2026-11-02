import { request } from '@/api/client';

/** 환불규정 현재 판. 주문서는 id 를 "이 판에 동의했다" 로 보낸다. */
export interface RefundPolicy {
  id: number;
  content: string;
  publishedAt: string;
}

export function fetchRefundPolicy() {
  return request<RefundPolicy>('/api/v1/policies/refund');
}
