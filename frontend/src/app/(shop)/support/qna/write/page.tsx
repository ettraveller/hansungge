import type { Metadata } from 'next';

import { QnaWriteView } from '@/views/board/QnaWriteView';

export const metadata: Metadata = {
  title: '문의 작성',
  // 작성 화면은 색인할 내용이 없다. 검색 결과에서 빈 폼으로 들어오게 두지 않는다
  robots: { index: false, follow: true },
};

/** 문의 작성 (`/support/qna/write`). 로그인이 필요하고, 가드는 화면 안에 있다. */
export default async function QnaWritePage({
  searchParams,
}: {
  searchParams: Promise<{ orderNo?: string }>;
}) {
  const { orderNo } = await searchParams;
  // 주소에 실려 오는 값이다. 주문번호 모양(HS20261113-000001)일 때만 넘긴다 —
  // 아무 글자나 문의 본문에 미리 꽂히게 두지 않는다.
  const safe = orderNo && /^[A-Z]{2}[0-9]{8}-[0-9]{6}$/.test(orderNo) ? orderNo : '';
  return <QnaWriteView boardType="GENERAL" orderNo={safe} />;
}
