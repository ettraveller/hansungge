import { adminApi } from '@/api/admin/client';
import type { PageResponse } from '@/types/api';
import type { EventStatus } from '@/types/board';

/** 관리자 이벤트 응답. 공개 응답과 달리 {@code active} 와 내린 이벤트가 온다. */
export interface AdminEvent {
  id: number;
  title: string;
  content?: string | null;
  thumbnailKey?: string | null;
  startAt: string;
  endAt: string;
  /** 서버가 현재 시각과 기간을 비교해 정한다. 화면에서 다시 계산하지 않는다 */
  status: EventStatus;
  viewCount: number;
  entryCount: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * 관리자가 보는 응모글.
 *
 * <p>공개 목록과 달리 <b>비밀 응모글의 내용까지</b> 온다. 당첨자를 뽑으려면 응모 내용을
 * 읽어야 하고, 그것이 이 목록의 목적이다. 이름은 관리자에게도 가려서 온다({@code 김**})
 * — 당첨자를 고르는 데 실명이 필요하지 않다.
 */
export interface AdminEventEntry {
  id: number;
  authorName: string;
  secret: boolean;
  content?: string | null;
  createdAt: string;
}

export interface AdminEventSearch {
  page?: number;
  size?: number;
}

/**
 * 이벤트 등록·수정 요청.
 *
 * <p>{@link startAt}/{@link endAt} 는 <b>오프셋이 붙은 ISO 문자열</b>이어야 한다.
 * 백엔드가 {@code OffsetDateTime} 으로 받는다 — 오프셋 없는 문자열을 보내면 400 이고,
 * 설령 통과하더라도 서버 기준으로 해석되어 기간이 9시간 어긋난다.
 */
export interface EventWriteRequest {
  title: string;
  content?: string | null;
  thumbnailKey?: string | null;
  startAt: string;
  endAt: string;
  active?: boolean;
}

export function listEvents(search: AdminEventSearch) {
  return adminApi.get<PageResponse<AdminEvent>>('/api/admin/events', {
    query: { page: search.page ?? 0, size: search.size ?? 20 },
  });
}

export function listEntries(eventId: number, page = 0) {
  return adminApi.get<PageResponse<AdminEventEntry>>(
    `/api/admin/events/${eventId}/entries`,
    { query: { page, size: 50 } },
  );
}

export function createEvent(body: EventWriteRequest) {
  return adminApi.post<AdminEvent>('/api/admin/events', { body });
}

export function updateEvent(id: number, body: EventWriteRequest) {
  return adminApi.put<AdminEvent>(`/api/admin/events/${id}`, { body });
}

/** 공지와 같다 — 행은 남고 노출만 멈춘다. 응모 기록이 딸려 있어 더욱 지울 수 없다. */
export function hideEvent(id: number) {
  return adminApi.delete<void>(`/api/admin/events/${id}`);
}
