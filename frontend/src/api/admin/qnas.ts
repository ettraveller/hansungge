import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';
import type { QnaBoardType, QnaStatus } from '@/types/board';

/**
 * 관리자 문의 응답.
 *
 * <p>공개 응답({@code QnaSummary})과 다른 점이 셋이다 — <b>본문이 실리고</b>,
 * <b>작성자 실명이 실리고</b>, <b>내린 글도 온다</b>. 누가 무엇을 물었는지 알아야
 * 응대할 수 있고, 내린 글도 무엇을 왜 내렸는지 확인할 수 있어야 한다.
 */
export interface AdminQna {
  id: number;
  boardType: QnaBoardType;
  title: string;
  question: string;
  userId: number;
  /** 가려지지 않은 이름 */
  authorName: string;
  secret: boolean;
  status: QnaStatus;
  answer?: string | null;
  answeredBy?: string | null;
  answeredAt?: string | null;
  viewCount: number;
  active: boolean;
  productId?: number | null;
  productName?: string | null;
  createdAt: string;
}

export interface AdminQnaSearch {
  boardType?: QnaBoardType | null;
  unansweredOnly?: boolean;
  page?: number;
  size?: number;
}

export function listQnas(search: AdminQnaSearch) {
  return adminApi.get<PageResponse<AdminQna>>('/api/admin/qnas', {
    query: {
      boardType: search.boardType ?? undefined,
      unansweredOnly: search.unansweredOnly ? true : undefined,
      page: search.page ?? 0,
      size: search.size ?? 20,
    },
  });
}

/** 대시보드 배지용. 미답변이 쌓이는 것이 이 게시판의 유일한 실패 방식이다. */
export function unansweredCount() {
  return adminApi.get<{ count: number }>('/api/admin/qnas/unanswered-count');
}

/** 답변은 문의당 하나다. 다시 부르면 덮어쓴다 (백엔드가 PUT 인 이유). */
export function answerQna(id: number, answer: string) {
  return adminApi.put<AdminQna>(`/api/admin/qnas/${id}/answer`, { body: { answer } });
}

/** 행을 지우지 않고 노출만 중단한다. 욕설·개인정보 노출은 답변 여부와 무관하게 내린다. */
export function hideQna(id: number) {
  return adminApi.delete<void>(`/api/admin/qnas/${id}`);
}
