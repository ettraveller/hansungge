/**
 * 게시판 타입 — 백엔드 {@code domain/board} DTO 와 1:1 대응한다.
 *
 * ⚠️ 백엔드는 `non_null` 직렬화라 null 필드를 응답에서 생략한다.
 * `| null` 로 적힌 값은 런타임에 `undefined` 로 도착할 수 있으므로,
 * 비교는 반드시 느슨한 `== null` 로 한다.
 */

// ---------------------------------------------------------------------
// 공지사항
// ---------------------------------------------------------------------

/** 공지 목록 한 줄. 본문과 작성 관리자 아이디는 공개 응답에 없다. */
export interface NoticeSummary {
  id: number;
  title: string;
  /** 목록 맨 위에 고정. 화면은 번호 대신 "공지" 배지를 그린다 */
  pinned: boolean;
  viewCount: number;
  createdAt: string;
}

export interface NoticeDetail {
  id: number;
  title: string;
  content: string;
  pinned: boolean;
  viewCount: number;
  createdAt: string;
  /** 조회수 증가로는 바뀌지 않는다 */
  updatedAt: string;
}

// ---------------------------------------------------------------------
// 문의 (문의게시판 · 제품문의)
// ---------------------------------------------------------------------

/**
 * 문의가 속한 게시판.
 *
 * <p>두 게시판은 같은 테이블을 쓰고 이 값으로만 갈린다. 상품 연결 여부로 가르지 않는
 * 이유는 백엔드 V12 주석에 있다 — 상품을 못 고른 제품문의가 문의게시판으로 샌다.
 */
export type QnaBoardType = 'GENERAL' | 'PRODUCT';

export const QNA_BOARD_LABEL: Record<QnaBoardType, string> = {
  GENERAL: '문의게시판',
  PRODUCT: '제품문의',
};

/** 문의 게시판별 화면 경로. 목록·상세·작성이 모두 이 아래에 있다. */
export const QNA_BOARD_PATH: Record<QnaBoardType, string> = {
  GENERAL: '/support/qna',
  PRODUCT: '/support/product-qna',
};

export type QnaStatus = 'PENDING' | 'ANSWERED';

export const QNA_STATUS_LABEL: Record<QnaStatus, string> = {
  PENDING: '접수',
  ANSWERED: '답변완료',
};

/**
 * 문의 목록 한 줄.
 *
 * <p>{@link authorName} 은 <b>백엔드가 이미 가린 값</b>이다(`김**`). 화면에서 자르지
 * 않는다 — 그러면 실명이 HTML 에 그대로 실려 나가고 가려진 것은 눈뿐이다.
 */
export interface QnaSummary {
  id: number;
  boardType: QnaBoardType;
  title: string;
  authorName: string;
  /** 잠금 표시. 본문은 본인·관리자만 볼 수 있다 */
  secret: boolean;
  status: QnaStatus;
  viewCount: number;
  createdAt: string;
  /** 제품문의에서 상품을 고른 경우에만 있다 */
  productId?: number | null;
  productName?: string | null;
}

export interface QnaDetail extends QnaSummary {
  question: string;
  /** 답변 전이면 없다 */
  answer?: string | null;
  answeredAt?: string | null;
  /** 로그인한 사람이 이 글의 작성자인가. 수정·삭제 버튼을 그릴 근거다 */
  mine: boolean;
}

export interface QnaWriteRequest {
  boardType: QnaBoardType;
  title: string;
  question: string;
  secret: boolean;
  productId?: number;
}

// ---------------------------------------------------------------------
// 이벤트
// ---------------------------------------------------------------------

/** 진행 상태. 서버가 현재 시각과 기간을 비교해 정해서 내려준다. */
export type EventStatus = 'UPCOMING' | 'OPEN' | 'CLOSED';

export const EVENT_STATUS_LABEL: Record<EventStatus, string> = {
  UPCOMING: '진행예정',
  OPEN: '진행중',
  CLOSED: '종료',
};

export interface EventSummary {
  id: number;
  title: string;
  thumbnailKey?: string | null;
  startAt: string;
  endAt: string;
  status: EventStatus;
  viewCount: number;
  entryCount: number;
}

export interface EventDetail extends EventSummary {
  content?: string | null;
  /** 로그인한 사람이 이미 응모했는가 */
  entered: boolean;
}

/** 이벤트 응모글. 제목이 없다 — 응모는 한 줄이면 된다 */
export interface EventEntry {
  id: number;
  authorName: string;
  secret: boolean;
  /** 비밀 응모글을 남이 보면 없다 */
  content?: string | null;
  createdAt: string;
  mine: boolean;
}
