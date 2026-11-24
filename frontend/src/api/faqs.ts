import { api } from '@/api/client';

/**
 * 자주 묻는 질문.
 *
 * <p>⚠️ 이 파일에 {@code 'use client'} 를 붙이지 않는다 — 서버 화면(고객센터·FAQ)과
 * 관리자 화면이 같이 쓴다.
 */
export interface Faq {
  id?: number;
  /** 묶음 이름 (주문·결제, 배송 …). 관리자가 적는다 */
  category: string;
  question: string;
  /** 글자 그대로. HTML 이 아니다 — 줄바꿈만 화면이 살린다 */
  answer: string;
  active: boolean;
}

/** 서버(FaqService · FaqReplaceRequest)와 같은 값이다. */
export const MAX_FAQS = 100;
export const MAX_FAQ_CATEGORY = 50;
export const MAX_FAQ_QUESTION = 300;
export const MAX_FAQ_ANSWER = 4000;

export const faqApi = {
  /** 손님 화면용 — 내건 것만. 관리자가 고치면 바로 보여야 한다 */
  list: () => api.get<Faq[]>('/api/v1/faqs', { cache: 'no-store' }),
};

/** 묶음 이름으로 모은다. 묶음의 차례는 그 묶음이 처음 나온 차례다. */
export function groupFaqs(faqs: Faq[]): { category: string; items: Faq[] }[] {
  const groups: { category: string; items: Faq[] }[] = [];
  for (const faq of faqs) {
    const found = groups.find((g) => g.category === faq.category);
    if (found) found.items.push(faq);
    else groups.push({ category: faq.category, items: [faq] });
  }
  return groups;
}
