import type { Metadata } from 'next';

import { NotFoundContent } from '@/components/ui/NotFoundContent';
import { ShopLayout } from '@/layouts/ShopLayout';

export const metadata: Metadata = { title: '페이지를 찾을 수 없습니다' };

/**
 * 어느 화면에도 맞지 않는 주소 (예: /about).
 *
 * <p>여기는 {@code (shop)} 레이아웃 밖이라 머리띠가 저절로 붙지 않는다. 쇼핑몰
 * 레이아웃을 직접 감싸서, 길을 잃은 손님이 메뉴와 검색을 그대로 쓸 수 있게 한다.
 */
export default function NotFound() {
  return (
    <ShopLayout>
      <NotFoundContent />
    </ShopLayout>
  );
}
