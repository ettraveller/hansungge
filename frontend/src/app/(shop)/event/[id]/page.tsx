import type { Metadata } from 'next';
import { notFound, unstable_rethrow } from 'next/navigation';

import { eventApi } from '@/api/board';
import { load } from '@/api/server';
import { ApiError } from '@/types/api';
import { EventDetailView } from '@/views/board/EventDetailView';

interface PageProps {
  params: Promise<{ id: string }>;
}

/**
 * 이벤트 상세 (`/event/[id]`).
 *
 * <p>이벤트 안내와 응모 목록을 <b>함께</b> 불러온다. 순차로 부르면 느린 쪽이 빠른 쪽을
 * 기다린다.
 *
 * <p>응모 목록은 실패해도 페이지를 죽이지 않는다 — 이벤트 내용이 이 화면의 본체이고,
 * 응모 현황은 부가 정보다.
 */
export default async function EventDetailPage({ params }: PageProps) {
  const id = toEventId((await params).id);
  if (id == null) notFound();

  const [event, entries] = await Promise.all([
    load(() => eventApi.detail(id), `이벤트 상세 ${id}`),
    load(() => eventApi.entries(id), `이벤트 응모 ${id}`),
  ]);

  if (!event.data) {
    // 없는 이벤트와 일시 장애를 구분한다. 후자를 404 로 처리하면 검색엔진이
    // 멀쩡한 이벤트 페이지를 색인에서 지운다
    if (event.error?.startsWith('EVENT_NOT_FOUND')) notFound();
    throw new Error(`이벤트를 불러올 수 없습니다: ${event.error}`);
  }

  return <EventDetailView event={event.data} entries={entries} />;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const id = toEventId((await params).id);
  if (id == null) return { title: '이벤트' };

  try {
    const event = await eventApi.detail(id);
    return {
      title: event.title,
      description: event.content?.slice(0, 120) ?? '한성금거래소 이벤트',
      openGraph: { title: event.title, type: 'article' },
    };
  } catch (e) {
    unstable_rethrow(e);
    if (!(e instanceof ApiError)) console.error(`[SSR] 이벤트 메타데이터 ${id}`, e);
    return { title: '이벤트' };
  }
}

function toEventId(raw: string): number | null {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : null;
}
