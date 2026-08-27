import type { Metadata } from 'next';
import { Suspense } from 'react';

import { LoginView } from '@/views/auth/LoginView';

export const metadata: Metadata = {
  title: '로그인',
  description: '한성금거래소 소셜 로그인',
  // 로그인 화면은 색인할 이유가 없다.
  robots: { index: false, follow: true },
};

/** useSearchParams(next=...) 를 쓰므로 Suspense 로 감싼다 (Next.js 16). */
export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginView />
    </Suspense>
  );
}
