import { Suspense } from 'react';

import { AdminLoginView } from '@/views/admin/AdminLoginView';

/**
 * useSearchParams(next=...) 를 쓰므로 Suspense 로 감싼다.
 * 감싸지 않으면 프리렌더 단계에서 빌드가 막힌다 (Next.js 16).
 */
export default function AdminLoginPage() {
  return (
    <Suspense fallback={null}>
      <AdminLoginView />
    </Suspense>
  );
}
