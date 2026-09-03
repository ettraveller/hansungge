import type { Metadata } from 'next';

import { QnaWriteView } from '@/views/board/QnaWriteView';

export const metadata: Metadata = {
  title: '제품문의 작성',
  robots: { index: false, follow: true },
};

/** 제품문의 작성 (`/support/product-qna/write`). */
export default function ProductQnaWritePage() {
  return <QnaWriteView boardType="PRODUCT" />;
}
