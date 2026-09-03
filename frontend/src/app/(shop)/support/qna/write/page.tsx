import type { Metadata } from 'next';

import { QnaWriteView } from '@/views/board/QnaWriteView';

export const metadata: Metadata = {
  title: '문의 작성',
  // 작성 화면은 색인할 내용이 없다. 검색 결과에서 빈 폼으로 들어오게 두지 않는다
  robots: { index: false, follow: true },
};

/** 문의 작성 (`/support/qna/write`). 로그인이 필요하고, 가드는 화면 안에 있다. */
export default function QnaWritePage() {
  return <QnaWriteView boardType="GENERAL" />;
}
