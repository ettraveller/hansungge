import { api } from '@/api/client';
import type { Popup } from '@/types/popup';

export const popupApi = {
  /**
   * 지금 띄울 팝업.
   *
   * <p>로그인 전 첫 화면에서 부른다. 빈 목록은 정상이다 — 팝업이 없는 날이 대부분이다.
   */
  live: () => api.get<Popup[]>('/api/v1/popups', { cache: 'no-store' }),
};
