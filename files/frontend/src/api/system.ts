import { api } from '@/api/client';
import type { HealthResponse } from '@/types/system';

/**
 * 도메인별 API 모듈의 참조 구현.
 * 화면 컴포넌트는 fetch 를 직접 호출하지 않고 항상 이 계층을 거친다.
 */
export const systemApi = {
  health: () => api.get<HealthResponse>('/api/v1/health', { cache: 'no-store' }),
};
