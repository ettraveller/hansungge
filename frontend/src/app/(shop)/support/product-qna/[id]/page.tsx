import type { Metadata } from 'next';
import { unstable_rethrow } from 'next/navigation';

import { qnaApi } from '@/api/board';
import { load } from '@/api/server';
import { ApiError } from '@/types/api';
import { QnaDetailView } from '@/views/board/QnaDetailView';
import { QnaSecretFallback } from '@/views/board/QnaSecretFallback';

interface PageProps {
  params: Promise<{ id: string }>;
}

/**
 * 제품문의 상세 (`/support/product-qna/[id]`).
 *
 * <p>문의게시판 상세와 같은 흐름이다 — 서버가 토큰 없이 먼저 부르고, 404 면 브라우저가
 * 토큰을 실어 다시 부른다. 자세한 이유는 {@code support/qna/[id]/page.tsx} 에 적었다.
 */
export default async function ProductQnaDetailPage({ params }: PageProps) {
  const id = toQnaId((await params).id);
  if (id == null) {
    return <QnaSecretFallback id={0} boardType="PRODUCT" />;
  }

  const qna = await load(() => qnaApi.detailPublic(id), `제품문의 상세 ${id}`);

  if (!qna.data) {
    if (qna.error?.startsWith('QNA_NOT_FOUND')) {
      return <QnaSecretFallback id={id} boardType="PRODUCT" />;
    }
    throw new Error(`제품문의를 불러올 수 없습니다: ${qna.error}`);
  }

  return <QnaDetailView qna={qna.data} />;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const id = toQnaId((await params).id);
  if (id == null) return { title: '제품문의' };

  try {
    const qna = await qnaApi.detailPublic(id);
    return {
      title: qna.title,
      description: qna.question?.slice(0, 120),
      robots: qna.status === 'ANSWERED' ? undefined : { index: false, follow: true },
    };
  } catch (e) {
    unstable_rethrow(e);
    if (!(e instanceof ApiError)) console.error(`[SSR] 제품문의 메타데이터 ${id}`, e);
    return { title: '제품문의', robots: { index: false, follow: true } };
  }
}

function toQnaId(raw: string): number | null {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : null;
}
