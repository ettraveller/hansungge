import type { Metadata } from 'next';

import { qnaApi } from '@/api/board';
import { load } from '@/api/server';
import { QnaListView } from '@/views/board/QnaListView';

export const metadata: Metadata = {
  title: '문의게시판',
  description:
    '한성금거래소 문의게시판. 주문·배송·매입에 관한 질문과 답변을 확인하고 직접 문의를 남기실 수 있습니다.',
};

interface PageProps {
  searchParams: Promise<{ page?: string }>;
}

/** 문의게시판 (`/support/qna`). 목록은 로그인 없이 보인다. */
export default async function QnaPage({ searchParams }: PageProps) {
  const page = toPage((await searchParams).page);
  const qnas = await load(() => qnaApi.list('GENERAL', { page: page - 1 }), '문의 목록');

  return <QnaListView boardType="GENERAL" qnas={qnas} page={page} />;
}

function toPage(raw: string | undefined): number {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : 1;
}
