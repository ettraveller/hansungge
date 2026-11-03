import type { Metadata } from 'next';

import { QnaWriteView } from '@/views/board/QnaWriteView';

export const metadata: Metadata = {
  title: '제품문의 작성',
  robots: { index: false, follow: true },
};

interface PageProps {
  searchParams: Promise<{ productId?: string }>;
}

/**
 * 제품문의 작성 (`/support/product-qna/write`).
 *
 * <p>{@code ?productId=} 로 들어오면 그 상품의 문의로 남는다 — 상품 상세의
 * 「상품문의」 탭에서 넘어오는 길이다. 없으면 상품에 묶이지 않은 일반 제품문의다.
 */
export default async function ProductQnaWritePage({ searchParams }: PageProps) {
  const raw = Number((await searchParams).productId);
  const productId = Number.isInteger(raw) && raw > 0 ? raw : undefined;

  return <QnaWriteView boardType="PRODUCT" productId={productId} />;
}
