'use client';

import { useEffect, useState } from 'react';

import type { HeroBanner } from '@/api/banners';
import { HeroSlider } from '@/components/home/HeroSlider';

/**
 * 관리자가 고치는 중인 꾸민 배너를 담아 두는 칸.
 *
 * <p>관리자 화면과 이 화면은 같은 탭 안의 iframe 이라 {@code sessionStorage} 를 함께
 * 쓴다. 주소로 넘기지 않는 이유는 두 가지다 — 배너 세 장의 내용을 전부 담으면 주소가
 * 수 킬로바이트가 되고, 이 주소를 누구나 열 수 있게 되므로 넘어온 내용을 여기서 다시
 * 전부 걸러야 한다. 담아 두는 쪽은 그 둘 다 없다.
 */
export const DRAFT_KEY = 'hanseongge:banner-draft';

/**
 * 고치는 중인 배너를 손님이 보는 그 컴포넌트로 그린다.
 *
 * <p>iframe 안에서 열린다. 관리자 창에서 바로 그리면 <b>휴대폰 모양을 볼 수 없다</b> —
 * 넓은 화면과 좁은 화면을 가르는 것이 창 폭을 보는 미디어 쿼리이고, 그 창은 모달이
 * 아니라 관리자가 쓰는 브라우저 창이기 때문이다. iframe 은 제 폭이 곧 제 뷰포트다.
 */
export function DraftHero() {
  const [banners, setBanners] = useState<HeroBanner[] | null>(null);

  useEffect(() => {
    try {
      const raw = sessionStorage.getItem(DRAFT_KEY);
      setBanners(raw ? (JSON.parse(raw) as HeroBanner[]) : []);
    } catch {
      // 담아 둔 것이 깨졌으면 빈 화면이 낫다. 관리자 화면에서 다시 열면 다시 담긴다.
      setBanners([]);
    }
  }, []);

  if (banners === null) return null;

  if (banners.length === 0) {
    return <p style={{ padding: 24, color: '#888' }}>보여줄 배너가 없습니다.</p>;
  }

  return <HeroSlider slides={[]} banners={banners} />;
}
