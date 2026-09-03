'use client';

import { useRestoreSession } from '@/hooks/useUserSession';

/**
 * 앱이 뜰 때 세션을 한 번 복구한다.
 *
 * <p>Access 토큰은 메모리에만 있어 새로고침하면 사라진다. HttpOnly Refresh 쿠키로
 * 다시 받아오는 일을 여기서 한다 — 화면을 그리지 않고 훅만 실행한다.
 *
 * <p>Provider 안에 두는 이유: react-query 훅을 쓰므로 QueryClientProvider 아래여야 한다.
 */
export function SessionBootstrap() {
  useRestoreSession();
  return null;
}
