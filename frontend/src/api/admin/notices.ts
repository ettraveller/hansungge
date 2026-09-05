import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';

/**
 * 관리자 공지 응답.
 *
 * <p>공개 응답({@code NoticeSummary})과 다른 점이 셋이다 — <b>본문이 실리고</b>,
 * <b>작성 관리자 아이디가 실리고</b>, <b>내린 공지도 온다</b>. 셋 다 고객에게 나가면
 * 안 되는 값이라 백엔드가 DTO 를 아예 나눠 두었다.
 */
export interface AdminNotice {
  id: number;
  title: string;
  content: string;
  /** 목록 맨 위 고정 */
  pinned: boolean;
  viewCount: number;
  /** 노출 여부. false 면 내려진 공지 */
  active: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminNoticeSearch {
  page?: number;
  size?: number;
}

/**
 * 공지 등록·수정 요청.
 *
 * <p>{@link active} 는 <b>수정에서만</b> 의미가 있다. 등록은 백엔드가 무시하고 항상
 * 노출로 시작한다 — 안 보이는 공지를 등록하는 일은 없다.
 */
export interface NoticeWriteRequest {
  title: string;
  content: string;
  pinned: boolean;
  active?: boolean;
}

export function listNotices(search: AdminNoticeSearch) {
  return adminApi.get<PageResponse<AdminNotice>>('/api/admin/notices', {
    query: { page: search.page ?? 0, size: search.size ?? 20 },
  });
}

export function createNotice(body: NoticeWriteRequest) {
  return adminApi.post<AdminNotice>('/api/admin/notices', { body });
}

export function updateNotice(id: number, body: NoticeWriteRequest) {
  return adminApi.put<AdminNotice>(`/api/admin/notices/${id}`, { body });
}

/**
 * 공지 내리기.
 *
 * <p>DELETE 지만 행은 남는다 — {@code active=false} 가 될 뿐이다. 공지는 "그때 그렇게
 * 안내했다"는 근거라, 지우면 나중에 분쟁이 생겼을 때 증명할 것이 없다.
 */
export function hideNotice(id: number) {
  return adminApi.delete<void>(`/api/admin/notices/${id}`);
}
