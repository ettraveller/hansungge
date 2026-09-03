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
 * 문의 상세 (`/support/qna/[id]`).
 *
 * <h2>서버가 먼저, 안 되면 브라우저가</h2>
 * 서버는 토큰이 없다(Access 토큰이 브라우저 메모리에만 있다). 그래서
 * <ol>
 *   <li>먼저 <b>토큰 없이</b> 부른다 — 공개 문의는 여기서 끝나고, 질문과 답변이
 *       초기 HTML 에 실려 검색에 걸린다. 이 게시판의 실질적인 콘텐츠가 그 문답이다</li>
 *   <li>404 면 브라우저가 <b>토큰을 실어</b> 다시 부른다 — 작성자 본인의 비밀글이면
 *       그때 열린다</li>
 * </ol>
 *
 * <p>없는 글과 남의 비밀글은 <b>서버가 일부러 같은 404</b>로 만든다(존재를 알리지 않는다).
 * 그래서 여기서도 둘을 구분하지 않고 한 번 더 물어보기만 한다.
 */
export default async function QnaDetailPage({ params }: PageProps) {
  const id = toQnaId((await params).id);
  if (id == null) {
    return <QnaSecretFallback id={0} boardType="GENERAL" />;
  }

  const qna = await load(() => qnaApi.detailPublic(id), `문의 상세 ${id}`);

  if (!qna.data) {
    if (qna.error?.startsWith('QNA_NOT_FOUND')) {
      // 비밀글일 수 있다. 브라우저에서 토큰으로 다시 시도한다
      return <QnaSecretFallback id={id} boardType="GENERAL" />;
    }
    throw new Error(`문의를 불러올 수 없습니다: ${qna.error}`);
  }

  return <QnaDetailView qna={qna.data} />;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const id = toQnaId((await params).id);
  if (id == null) return { title: '문의게시판' };

  try {
    const qna = await qnaApi.detailPublic(id);
    return {
      title: qna.title,
      description: qna.question?.slice(0, 120),
      // 비밀글은 애초에 여기 오지 않지만, 문의 상세는 색인 가치가 낮은 것도 많다.
      // 답변이 달린 글만 색인 대상으로 둔다
      robots: qna.status === 'ANSWERED' ? undefined : { index: false, follow: true },
    };
  } catch (e) {
    unstable_rethrow(e);
    if (!(e instanceof ApiError)) console.error(`[SSR] 문의 메타데이터 ${id}`, e);
    return { title: '문의게시판', robots: { index: false, follow: true } };
  }
}

function toQnaId(raw: string): number | null {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : null;
}
