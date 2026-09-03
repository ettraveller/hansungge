import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Suspense } from 'react';

import { AuthCallbackView } from '@/views/auth/AuthCallbackView';
import type { OAuthProviderType } from '@/types/user';

export const metadata: Metadata = {
  title: '로그인 처리 중',
  // 인가코드가 붙은 주소다. 색인되어서는 안 된다.
  robots: { index: false, follow: false, nocache: true },
};

const SUPPORTED = ['google', 'naver', 'kakao'] as const;

/**
 * 소셜 제공자 콜백. 최종 주소는 {@code /auth/callback/{google|naver|kakao}} 이며,
 * 각 제공자 콘솔에 등록한 값과 <b>글자 하나까지</b> 같아야 한다.
 *
 * <p>쇼핑몰 레이아웃 그룹 밖에 둔다 — 헤더·GNB·푸터가 붙은 채로 잠깐 떴다 사라지면
 * 화면이 덜컹거린다.
 *
 * <p>Next.js 16 에서 {@code params} 는 Promise 다. 반드시 await 한다.
 */
export default async function OAuthCallbackPage({
  params,
}: {
  params: Promise<{ provider: string }>;
}) {
  const { provider } = await params;
  const normalized = provider.toLowerCase();
  if (!SUPPORTED.includes(normalized as (typeof SUPPORTED)[number])) notFound();

  return (
    <Suspense fallback={null}>
      <AuthCallbackView provider={normalized.toUpperCase() as OAuthProviderType} />
    </Suspense>
  );
}
