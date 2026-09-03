import type { Metadata } from 'next';

import { qnaApi } from '@/api/board';
import { load } from '@/api/server';
import { QnaListView } from '@/views/board/QnaListView';

export const metadata: Metadata = {
  title: '제품문의',
  description:
    '한성금거래소 제품문의. 골드바·주얼리의 규격·재고·각인에 관한 질문과 답변을 확인하실 수 있습니다.',
};

interface PageProps {
  searchParams: Promise<{ page?: string }>;
}

/**
 * 제품문의 (`/support/product-qna`).
 *
 * <p>문의게시판과 <b>같은 화면 컴포넌트</b>를 쓴다. 백엔드도 한 테이블을 쓰고
 * {@code boardType} 으로만 갈린다 — 규칙이 같은 것을 두 벌로 만들지 않는다.
 */
export default async function ProductQnaPage({ searchParams }: PageProps) {
  const page = toPage((await searchParams).page);
  const qnas = await load(() => qnaApi.list('PRODUCT', { page: page - 1 }), '제품문의 목록');

  return <QnaListView boardType="PRODUCT" qnas={qnas} page={page} />;
}

function toPage(raw: string | undefined): number {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : 1;
}
