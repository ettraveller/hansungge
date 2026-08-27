'use client';

import { useQuery } from '@tanstack/react-query';
import { systemApi } from '@/api/system';

/**
 * 도메인 훅의 참조 구현.
 *
 * 화면 컴포넌트는 fetch/axios 를 직접 부르지 않는다.
 *   화면(view) → 훅(hooks) → API 모듈(api) → client.ts
 * 순서를 지키면 인증 헤더·오류 포맷·베이스 URL 이 한 곳에서만 관리된다.
 */
export function useHealth() {
  return useQuery({
    queryKey: ['system', 'health'],
    queryFn: systemApi.health,
    refetchInterval: 60_000,
  });
}
