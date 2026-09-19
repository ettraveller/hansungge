import type { Metadata } from 'next';
import { Suspense } from 'react';

import { SignUpView } from '@/views/auth/SignUpView';

export const metadata: Metadata = {
  title: '회원가입',
  robots: { index: false, follow: true },
};

/** useSearchParams(next=...) 를 쓰므로 Suspense 로 감싼다 (Next.js 16). */
export default function SignUpPage() {
  return (
    <Suspense fallback={null}>
      <SignUpView />
    </Suspense>
  );
}
