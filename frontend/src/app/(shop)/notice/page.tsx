import type { Metadata } from 'next';

import { noticeApi } from '@/api/board';
import { load } from '@/api/server';
import { NoticeListView } from '@/views/board/NoticeListView';

export const metadata: Metadata = {
  title: '공지사항',
  description:
    '한성금거래소 공지사항. 배송 일정, 지점 운영, 시세 고시에 관한 안내를 확인하실 수 있습니다.',
};

interface PageProps {
  searchParams: Promise<{ page?: string }>;
}

/**
 * 공지사항 목록 (`/notice`).
 *
 * <p>화면과 URL 은 1-base, API 는 0-base 다. 변환은 여기서 한 번만 한다 —
 * 상품 목록(`/shop`)과 같은 방식이다.
 */
export default async function NoticePage({ searchParams }: PageProps) {
  const page = toPage((await searchParams).page);

  const notices = await load(() => noticeApi.list({ page: page - 1 }), '공지 목록');

  return <NoticeListView notices={notices} page={page} />;
}

/** 조작된 값(`?page=-1`, `?page=abc`)이 API 로 넘어가지 않게 막는다. */
function toPage(raw: string | undefined): number {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : 1;
}
