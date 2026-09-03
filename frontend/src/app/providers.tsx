'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';

import { SessionBootstrap } from '@/components/auth/SessionBootstrap';

/**
 * 서버 상태 관리 Provider.
 *
 * 시세는 자주 바뀌므로 staleTime 을 짧게 잡는다. 다만 "가격이 몇 초 늦게 보이는 것"과
 * "틀린 가격으로 주문되는 것"은 다른 문제다 — 후자는 주문 시점 서버 재계산이 막는다.
 */
export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={client}>
      <SessionBootstrap />
      {children}
    </QueryClientProvider>
  );
}
