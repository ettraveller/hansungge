import { adminApi } from '@/api/admin/client';

/** 메인 배너 한 장. 문구·버튼까지 이미지 안에 들어 있다. */
export interface AdminHeroBanner {
  id: number;
  imageKey: string;
  linkHref?: string | null;
  altText?: string | null;
  sort: number;
  active: boolean;
}

export interface HeroBannerInput {
  imageKey: string;
  linkHref?: string | null;
  altText?: string | null;
}

export function listBanners() {
  return adminApi.get<AdminHeroBanner[]>('/api/admin/banners');
}

/**
 * 메인 배너 전체 교체.
 *
 * <p>보낸 것이 곧 전체다. 한 장씩 더하고 지우지 않는다 — 그 사이에 반쯤 섞인 배너가
 * 손님 화면에 걸린다. 빈 배열이면 배너를 모두 내린다.
 */
export function replaceBanners(banners: HeroBannerInput[]) {
  return adminApi.put<AdminHeroBanner[]>('/api/admin/banners', { body: { banners } });
}
