'use client';

import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { useRestoreSession, useUserSession } from '@/hooks/useUserSession';

/**
 * 앱이 뜰 때 세션을 한 번 복구하고, 세션이 끝나는 순간을 지켜본다.
 *
 * <p>Access 토큰은 메모리에만 있어 새로고침하면 사라진다. HttpOnly Refresh 쿠키로
 * 다시 받아오는 일을 여기서 한다 — 화면을 그리지 않고 훅만 실행한다.
 *
 * <p>Provider 안에 두는 이유: react-query 훅을 쓰므로 QueryClientProvider 아래여야 한다.
 */
export function SessionBootstrap() {
  useRestoreSession();

  /*
   * 로그아웃이 보이면 서버 상태 캐시를 통째로 비운다.
   *
   * 로그아웃 경로는 하나가 아니다 — 버튼, 401 자동 만료(authedClient), 재발급 실패.
   * 경로마다 각자 지우게 두면 하나는 빠뜨린다. 실제로 버튼 경로가 회원 정보 캐시만
   * 지우고 장바구니를 남겨서, 로그아웃한 화면의 머리띠에 장바구니 개수가 그대로
   * 떠 있었다 (2026-11-17). 그래서 "인증이 꺼지는 순간"을 여기 한 곳에서 본다.
   *
   * 통째로 비우는 이유 — 회원 키만 골라 지우는 방식이 바로 그 빠뜨림을 만든다.
   * 공개 데이터(시세·상품)는 다시 받으면 그만이고, 회원 데이터는 남으면 사고다.
   * 같은 이유로, 로그아웃 없이 다른 계정으로 바뀌는 경우(id 가 달라짐)도 비운다 —
   * 앞 사람의 장바구니·주문이 뒷 사람에게 보이면 안 된다.
   */
  const { isAuthenticated, user } = useUserSession();
  const queryClient = useQueryClient();
  const previous = useRef<{ authed: boolean; userId: number | null }>({
    authed: false,
    userId: null,
  });

  useEffect(() => {
    const userId = user?.id ?? null;
    const prev = previous.current;
    const signedOut = prev.authed && !isAuthenticated;
    const switched = prev.userId != null && userId != null && prev.userId !== userId;
    if (signedOut || switched) {
      queryClient.clear();
    }
    previous.current = { authed: isAuthenticated, userId };
  }, [isAuthenticated, user, queryClient]);

  return null;
}
