import type { Metadata } from 'next';
import { notFound, unstable_rethrow } from 'next/navigation';

import { noticeApi } from '@/api/board';
import { load } from '@/api/server';
import { ApiError } from '@/types/api';
import { NoticeDetailView } from '@/views/board/NoticeDetailView';

interface PageProps {
  params: Promise<{ id: string }>;
}

/**
 * 공지 상세 (`/notice/[id]`).
 *
 * <p>없는 글과 일시 장애를 <b>반드시 구분</b>한다. 백엔드가 잠깐 죽었을 때 404 로
 * 처리하면 검색엔진이 멀쩡한 공지 페이지를 색인에서 지운다 — 상품 상세와 같은 판단이다.
 */
export default async function NoticeDetailPage({ params }: PageProps) {
  const id = toNoticeId((await params).id);
  if (id == null) notFound();

  const notice = await load(() => noticeApi.detail(id), `공지 상세 ${id}`);

  if (!notice.data) {
    if (notice.error?.startsWith('NOTICE_NOT_FOUND')) notFound();
    throw new Error(`공지사항을 불러올 수 없습니다: ${notice.error}`);
  }

  return <NoticeDetailView notice={notice.data} />;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const id = toNoticeId((await params).id);
  if (id == null) return { title: '공지사항' };

  try {
    const notice = await noticeApi.detail(id);
    return {
      title: notice.title,
      description: notice.content.slice(0, 120),
      openGraph: { title: notice.title, type: 'article' },
    };
  } catch (e) {
    unstable_rethrow(e);
    if (!(e instanceof ApiError)) console.error(`[SSR] 공지 메타데이터 ${id}`, e);
    // 메타데이터 실패가 페이지를 막지 않는다
    return { title: '공지사항' };
  }
}

function toNoticeId(raw: string): number | null {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : null;
}
