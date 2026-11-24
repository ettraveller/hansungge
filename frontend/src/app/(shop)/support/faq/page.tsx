import type { Metadata } from 'next';

import { faqApi } from '@/api/faqs';
import { load } from '@/api/server';
import { FaqView } from '@/views/board/FaqView';

export const metadata: Metadata = {
  title: '자주 묻는 질문',
  description: '한성금거래소 자주 묻는 질문. 주문·결제, 배송, 교환·반품, 매입에 관해 많이 받는 질문과 답입니다.',
};

/** 관리자가 고치면 바로 보여야 한다. */
export const dynamic = 'force-dynamic';

export default async function FaqPage() {
  const faqs = await load(() => faqApi.list(), '자주 묻는 질문');
  return <FaqView faqs={faqs.data} />;
}
