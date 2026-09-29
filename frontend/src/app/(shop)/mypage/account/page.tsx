import type { Metadata } from 'next';
import { Suspense } from 'react';

import { MyPageView } from '@/views/auth/MyPageView';

export const metadata: Metadata = {
  title: '계정 관리',
  robots: { index: false, follow: false },
};

/** useSearchParams(linked=1 — 소셜 연결 직후 안내)를 쓰므로 Suspense 로 감싼다 (Next.js 16). */
export default function AccountPage() {
  return (
    <Suspense fallback={null}>
      <MyPageView />
    </Suspense>
  );
}
