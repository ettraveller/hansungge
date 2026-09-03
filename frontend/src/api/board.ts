import { api } from '@/api/client';
import { authed } from '@/api/authedClient';
import type { PageResponse } from '@/types/api';
import type {
  EventDetail,
  EventEntry,
  EventSummary,
  NoticeDetail,
  NoticeSummary,
  QnaBoardType,
  QnaDetail,
  QnaSummary,
  QnaWriteRequest,
} from '@/types/board';

/**
 * 게시판 API.
 *
 * <h2>공개 조회와 인증 요청이 나뉘어 있다</h2>
 * 목록·상세는 {@link api} 를 쓴다 — 서버 컴포넌트에서 부를 수 있어야 하기 때문이다.
 * 작성·수정·응모는 {@link authed} 를 쓴다 — Access 토큰이 <b>메모리에만</b> 있어
 * 브라우저에서만 부를 수 있다.
 *
 * <p>이 구분은 취향이 아니라 제약이다. 서버 컴포넌트에서 {@code authed} 를 부르면
 * 토큰이 없어 항상 401 이다.
 */

/** 목록 API 는 0-base 페이지를 받는다. 화면의 1-base 와 변환은 호출부가 한다. */
export interface BoardListQuery {
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------
// 공지사항 — 전부 공개
// ---------------------------------------------------------------------

export const noticeApi = {
  list: (query: BoardListQuery = {}) =>
    api.get<PageResponse<NoticeSummary>>('/api/v1/notices', {
      query: { page: query.page, size: query.size },
      // 공지는 초 단위로 바뀌지 않는다. 그렇다고 오래 굳히면 긴급 공지가 늦게 뜬다
      next: { revalidate: 60 },
    }),

  detail: (id: number) =>
    api.get<NoticeDetail>(`/api/v1/notices/${id}`, {
      // 조회수가 응답에 들어 있어 캐시하면 숫자가 멈춘 것처럼 보인다
      cache: 'no-store',
    }),
};

// ---------------------------------------------------------------------
// 문의 — 목록·상세는 공개, 쓰기는 로그인
// ---------------------------------------------------------------------

export const qnaApi = {
  list: (boardType: QnaBoardType, query: BoardListQuery = {}) =>
    api.get<PageResponse<QnaSummary>>('/api/v1/qnas', {
      query: { boardType, page: query.page, size: query.size },
      // 문의는 새 글이 바로 보여야 한다. 방금 쓴 글이 목록에 없으면 실패로 읽힌다
      cache: 'no-store',
    }),

  /**
   * 문의 상세 — <b>토큰 없이</b>.
   *
   * <p>서버 컴포넌트가 쓴다. 공개 문의는 이것으로 충분하고, 그래야 질문과 답변이 초기
   * HTML 에 실려 검색에 걸린다. "배송 얼마나 걸리나요" 같은 문답이 이 게시판의 실질적인
   * 콘텐츠다.
   *
   * <p>비밀글이면 404 가 온다. 그때 {@link detail} 로 다시 부르는 것은 브라우저 쪽 일이다.
   */
  detailPublic: (id: number) =>
    api.get<QnaDetail>(`/api/v1/qnas/${id}`, { cache: 'no-store' }),

  /**
   * 문의 상세 — <b>토큰을 실어서</b>.
   *
   * <p>비밀글의 본문은 작성자와 관리자만 받는다. 그 판단을 서버가 토큰으로 하므로
   * 브라우저에서만 부를 수 있다.
   */
  detail: (id: number) => authed<QnaDetail>(`/api/v1/qnas/${id}`),

  write: (body: QnaWriteRequest) =>
    authed<QnaDetail>('/api/v1/qnas', { method: 'POST', body }),

  update: (id: number, body: Omit<QnaWriteRequest, 'boardType'>) =>
    authed<QnaDetail>(`/api/v1/qnas/${id}`, { method: 'PUT', body }),

  remove: (id: number) => authed<void>(`/api/v1/qnas/${id}`, { method: 'DELETE' }),

  /** 내가 쓴 문의. 마이페이지와 게시판 상단 "내 문의" 가 함께 쓴다 */
  mine: (query: BoardListQuery = {}) =>
    authed<PageResponse<QnaSummary>>('/api/v1/me/qnas', {
      query: { page: query.page, size: query.size },
    }),
};

// ---------------------------------------------------------------------
// 이벤트
// ---------------------------------------------------------------------

export const eventApi = {
  list: (query: BoardListQuery = {}) =>
    api.get<PageResponse<EventSummary>>('/api/v1/events', {
      query: { page: query.page, size: query.size },
      next: { revalidate: 60 },
    }),

  detail: (id: number) => api.get<EventDetail>(`/api/v1/events/${id}`, { cache: 'no-store' }),

  entries: (eventId: number, query: BoardListQuery = {}) =>
    api.get<PageResponse<EventEntry>>(`/api/v1/events/${eventId}/entries`, {
      query: { page: query.page, size: query.size },
      cache: 'no-store',
    }),

  enter: (eventId: number, content: string, secret: boolean) =>
    authed<EventEntry>(`/api/v1/events/${eventId}/entries`, {
      method: 'POST',
      body: { content, secret },
    }),
};
