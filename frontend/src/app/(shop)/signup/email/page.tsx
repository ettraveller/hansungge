import type { Metadata } from 'next';
import { Suspense } from 'react';

import { EmailSignUpView } from '@/views/auth/EmailSignUpView';

export const metadata: Metadata = {
  title: '회원가입',
  // 가입 화면은 검색 결과에 올릴 이유가 없다. 링크는 따라가게 둔다.
  robots: { index: false, follow: true },
};

/** `useSearchParams` 를 쓰므로 Suspense 로 감싼다 (Next.js 16). */
export default function EmailSignUpPage() {
  return (
    <Suspense fallback={null}>
      <EmailSignUpView />
    </Suspense>
  );
}
