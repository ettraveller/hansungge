import type { Metadata } from 'next';

import { eventApi } from '@/api/board';
import { load } from '@/api/server';
import { EventListView } from '@/views/board/EventListView';

export const metadata: Metadata = {
  title: '이벤트',
  description: '한성금거래소 이벤트. 진행 중인 이벤트를 확인하고 참여하실 수 있습니다.',
};

interface PageProps {
  searchParams: Promise<{ page?: string }>;
}

/** 이벤트 목록 (`/event`). */
export default async function EventPage({ searchParams }: PageProps) {
  const page = toPage((await searchParams).page);
  const events = await load(() => eventApi.list({ page: page - 1 }), '이벤트 목록');

  return <EventListView events={events} page={page} />;
}

function toPage(raw: string | undefined): number {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : 1;
}
