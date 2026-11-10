import { api } from '@/api/client';

/** 손님 화면에 걸리는 메인 배너. */
export interface HeroBanner {
  id: number;
  imageKey: string;
  /** 휴대폰용 이미지. 없으면 가로용 한 장을 그대로 쓴다 */
  mobileImageKey?: string | null;
  linkHref?: string | null;
  altText?: string | null;
  sort: number;
  active: boolean;
}

export const bannerApi = {
  // 관리자가 바꾸면 바로 보여야 한다. 메인은 자주 열리는 화면이라 캐시가 남으면
  // "바꿨는데 안 바뀐다"가 된다.
  list: () => api.get<HeroBanner[]>('/api/v1/banners', { cache: 'no-store' }),
};
