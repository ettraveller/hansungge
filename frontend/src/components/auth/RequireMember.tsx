'use client';

import { useRouter } from 'next/navigation';
import { useEffect, type CSSProperties, type ReactNode } from 'react';

import { useUserSession } from '@/hooks/useUserSession';

/**
 * 로그인이 필요한 화면의 문지기.
 *
 * <h2>왜 필요한가</h2>
 * Access 토큰은 <b>메모리에만</b> 있다. 새로고침하거나 주소를 직접 열면 토큰이 없는 채로
 * 시작하고, HttpOnly 쿠키로 되살리는 동안 잠깐 "로그인 안 된 상태"를 지난다.
 *
 * <p>그 순간에 API 를 부르면 401 이 오고, 화면은 그것을 "주문을 찾을 수 없습니다"로 읽는다.
 * <b>멀쩡한 세션인데 없는 주문처럼 보이는</b> 것이다. 그래서 복원이 끝날 때까지 기다린 뒤에만
 * 안쪽을 그린다.
 *
 * <p>장바구니(CartView)가 같은 처리를 직접 하고 있었다. 주문·결제 화면이 늘면서 같은 코드를
 * 네 번 쓰게 되어 이리로 모았다.
 */
export function RequireMember({
  children,
  next,
  loadingText = '확인 중…',
}: {
  children: ReactNode;
  /** 로그인 뒤 돌아올 주소. 넘기지 않으면 지금 주소를 쓴다 */
  next?: string;
  loadingText?: string;
}) {
  const router = useRouter();
  const { restored, isAuthenticated } = useUserSession();

  useEffect(() => {
    if (restored && !isAuthenticated) {
      const here =
        typeof window === 'undefined' ? '/' : window.location.pathname + window.location.search;
      router.replace(`/login?reason=required&next=${encodeURIComponent(next ?? here)}`);
    }
  }, [restored, isAuthenticated, next, router]);

  if (!restored) {
    return <p style={PLACEHOLDER}>{loadingText}</p>;
  }
  if (!isAuthenticated) {
    return <p style={PLACEHOLDER}>로그인 화면으로 이동합니다…</p>;
  }
  return <>{children}</>;
}

const PLACEHOLDER: CSSProperties = {
  padding: '60px 16px',
  textAlign: 'center',
  color: 'var(--gray)',
  fontSize: 14,
};
