import type { Metadata } from 'next';
import { priceApi } from '@/api/prices';
import { load } from '@/api/server';
import { PriceView } from '@/views/PriceView';

export const metadata: Metadata = {
  title: '금 시세',
  description:
    '한성금거래소 금·은·백금 실시간 고시 시세. 1g 기준 매입가·판매가를 확인하세요.',
};

/**
 * 요청 시점 렌더링을 명시한다.
 *
 * 이 페이지의 내용은 <b>시세</b>다. 빌드 시점에 굳히면 그 순간의 가격이 화면에 박히고,
 * 시세가 바뀌어도 재배포 전까지 옛 값이 나간다. no-store fetch 때문에 Next.js 가 어차피
 * 동적으로 판정하지만, 의도를 코드로 남겨 두면 나중에 캐시를 붙이는 사람이 이 사실을 본다.
 */
export const dynamic = 'force-dynamic';

/** 금시세 (`/price`). */
export default async function PricePage() {
  const prices = await load(() => priceApi.current(), '시세 페이지');
  return <PriceView prices={prices} />;
}
