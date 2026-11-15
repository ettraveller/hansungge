import { adminApi } from '@/api/admin/client';
import type { BannerContent, BannerKind } from '@/api/banners';

/** 메인 배너 한 장. 사진 한 장이거나(IMAGE), 화면에서 꾸민 것이다(DESIGN). */
export interface AdminHeroBanner {
  id: number;
  kind: BannerKind;
  /** DESIGN 일 때의 내용 */
  content?: BannerContent | null;
  imageKey?: string | null;
  mobileImageKey?: string | null;
  linkHref?: string | null;
  altText?: string | null;
  sort: number;
  active: boolean;
}

export interface HeroBannerInput {
  /** 비우면 IMAGE 로 본다 */
  kind?: BannerKind;
  /** DESIGN 일 때 보낸다. 서버가 다듬어 저장한다 */
  content?: BannerContent | null;
  imageKey?: string | null;
  mobileImageKey?: string | null;
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
