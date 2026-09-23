import type { Metadata } from 'next';
import { Suspense } from 'react';

import { CodeLoginView } from '@/views/auth/CodeLoginView';

export const metadata: Metadata = {
  title: '인증번호 로그인',
  robots: { index: false, follow: true },
};

/** useSearchParams(next=...) 를 쓰므로 Suspense 로 감싼다 (Next.js 16). */
export default function SmsLoginPage() {
  return (
    <Suspense fallback={null}>
      <CodeLoginView />
    </Suspense>
  );
}
