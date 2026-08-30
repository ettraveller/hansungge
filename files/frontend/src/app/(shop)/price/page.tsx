import type { Metadata } from 'next';
import { priceApi } from '@/api/prices';
import { load } from '@/api/server';
import { METAL_ORDER, type MetalType, type PriceHistoryPoint } from '@/types/price';
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

/** 시세 보드의 추이 그래프 기간. 메인과 같아야 두 화면이 같은 그림을 보여준다. */
const HISTORY_DAYS = 30;

/** 금시세 (`/price`). */
export default async function PricePage() {
  const [prices, histories] = await Promise.all([
    load(() => priceApi.current(), '시세 페이지'),
    loadHistories(),
  ]);

  return <PriceView prices={prices} histories={histories} />;
}

/**
 * 금속별 30일 추이. 호버할 때마다 부르지 않으려고 5종을 미리 받아 둔다.
 *
 * <p>한 금속이 실패해도 나머지는 그린다 — 그래프가 없다고 시세표까지 사라지면 안 된다.
 */
async function loadHistories(): Promise<Partial<Record<MetalType, PriceHistoryPoint[]>>> {
  const entries = await Promise.all(
    METAL_ORDER.map(async (metal) => {
      const result = await load(() => priceApi.history(metal, HISTORY_DAYS), `시세 추이 (${metal})`);
      return [metal, result.data ?? []] as const;
    }),
  );
  return Object.fromEntries(entries);
}
