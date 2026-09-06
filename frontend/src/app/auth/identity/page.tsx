import type { Metadata } from 'next';
import { Suspense } from 'react';

import { IdentityCallbackView } from '@/views/auth/IdentityCallbackView';

export const metadata: Metadata = {
  title: '본인확인',
  robots: { index: false, follow: false },
};

/**
 * 본인확인 복귀 화면 (`/auth/identity`).
 *
 * <p>`(shop)` 그룹 <b>밖</b>에 둔다. 소셜 콜백 화면과 같은 이유다 — 헤더·GNB·푸터가
 * 붙은 채로 잠깐 떴다 사라지면 화면이 덜컹거린다.
 */
export default function IdentityCallbackPage() {
  return (
    <Suspense fallback={null}>
      <IdentityCallbackView />
    </Suspense>
  );
}
