import { api } from '@/api/client';

/**
 * 상단 유틸 띠의 링크 하나 (V48).
 *
 * <p>아이콘이 있으면 그림 버튼으로, 없으면 글자 링크로 그린다. 갈래를 따로 적는 칸은
 * 없다 — "아이콘 링크인데 아이콘이 없다" 가 생길 수 없게 한다.
 */
export interface TopLink {
  /** 저장된 것에만 있다. 관리자 화면에서 막 더한 줄에는 없다 */
  id?: number;
  /** 글자 링크의 글자. 아이콘 링크면 화면 낭독기가 읽는 이름이라 비울 수 없다 */
  label: string;
  /** `https://…` 이거나 `/shop` 같은 사이트 안 경로 */
  href: string;
  /** 올린 아이콘의 미디어 키. 없으면 글자 링크다 */
  iconKey?: string | null;
}

export const topLinksApi = {
  /**
   * 관리자가 고치면 바로 보여야 한다. 띠는 모든 화면에 있어서, 캐시가 남으면
   * "링크를 걸었는데 안 보인다" 가 화면마다 반복된다 (푸터와 같은 이유).
   */
  get: () => api.get<TopLink[]>('/api/v1/top-links', { cache: 'no-store' }),
};
