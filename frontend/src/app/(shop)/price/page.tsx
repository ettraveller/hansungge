import type { Metadata } from 'next';
import { priceApi } from '@/api/prices';
import { load } from '@/api/server';
import {
  DEFAULT_DAYS,
  DEFAULT_METAL,
  RANGES,
} from '@/components/price/PriceHistorySection';
import { METAL_ORDER, type MetalType, type PriceHistoryPoint } from '@/types/price';
import { PriceView } from '@/views/PriceView';

export const metadata: Metadata = {
  title: '금 시세',
  description:
    '한성금거래소 금·은·백금 실시간 고시 시세. 1g 기준 매입가·판매가와 기간별 추이를 확인하세요.',
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
const BOARD_DAYS = 30;

interface PageProps {
  searchParams: Promise<{ metal?: string; days?: string; all?: string }>;
}

/**
 * 금시세 (`/price`).
 *
 * <p>선택한 금속과 기간이 <b>URL 에 있다.</b> 그래서 그래프와 표가 서버에서 그려지고,
 * 링크로 공유되며, 자바스크립트 없이도 동작한다.
 */
export default async function PricePage({ searchParams }: PageProps) {
  const params = await searchParams;
  const metal = toMetal(params.metal);
  const days = toDays(params.days);
  const expanded = params.all === '1';

  const [prices, boardHistories, sell, buy] = await Promise.all([
    load(() => priceApi.current(), '시세 페이지'),
    loadBoardHistories(),
    // 그래프와 표는 <b>선택한 금속만</b> 받는다. 5종을 1년치로 받으면 5천 점이
    // 초기 HTML 에 실려 페이지가 수백 KB 가 된다 — 화면에는 하나만 그려지는데도.
    load(() => priceApi.history(metal, days, 'SELL'), `시세 추이 ${metal}/SELL`),
    load(() => priceApi.history(metal, days, 'BUY'), `시세 추이 ${metal}/BUY`),
  ]);

  return (
    <PriceView
      prices={prices}
      histories={boardHistories}
      metal={metal}
      days={days}
      expanded={expanded}
      sell={slim(sell.data)}
      buy={slim(buy.data)}
    />
  );
}

/**
 * 화면이 쓰는 네 필드만 남긴다.
 *
 * <p>서버 컴포넌트가 자식에게 넘긴 props 는 <b>직렬화되어 HTML 안에 그대로 실린다.</b>
 * 이력 API 는 관리자 화면용 필드까지 함께 주는데({@code policyMode}, {@code createdBy},
 * {@code effectiveTo}), 그 중 {@code changeReason} 은 <b>고시할 때 관리자가 남긴 메모</b>다.
 * 지금은 "개발용 더미 데이터"지만 운영에서는 매입처나 마진 사정이 적힌다 — 그게 모든
 * 방문자의 페이지 소스에 들어간다.
 *
 * <p>부수적으로 초기 HTML 도 가벼워진다. 기본 화면에만 이력이 430점 실린다.
 *
 * <p>{@link PriceHistoryPoint} 는 원래 이 네 필드만 선언하고 있었다. 타입이 말하던 것을
 * 런타임에도 사실로 만드는 셈이다.
 */
function slim(points: PriceHistoryPoint[] | null): PriceHistoryPoint[] {
  return (points ?? []).map((p) => ({
    pricePerGram: p.pricePerGram,
    pricePerDon: p.pricePerDon,
    effectiveFrom: p.effectiveFrom,
    current: p.current,
  }));
}

/**
 * 시세 보드용 30일 추이. 호버할 때마다 부르지 않으려고 5종을 미리 받아 둔다.
 *
 * <p>한 금속이 실패해도 나머지는 그린다 — 그래프가 없다고 시세표까지 사라지면 안 된다.
 */
async function loadBoardHistories(): Promise<Partial<Record<MetalType, PriceHistoryPoint[]>>> {
  const entries = await Promise.all(
    METAL_ORDER.map(async (metal) => {
      const result = await load(() => priceApi.history(metal, BOARD_DAYS), `시세 추이 (${metal})`);
      return [metal, slim(result.data)] as const;
    }),
  );
  return Object.fromEntries(entries);
}

/** 주소창에 아무 값이나 넣어도 API 로 넘어가지 않게 막는다. */
function toMetal(raw: string | undefined): MetalType {
  return METAL_ORDER.includes(raw as MetalType) ? (raw as MetalType) : DEFAULT_METAL;
}

/**
 * 기간.
 *
 * <p>미리 정한 값만 받는다. 백엔드가 1~365 를 허용하지만 임의의 숫자를 그대로 넘기면
 * 화면에 없는 상태가 주소로 만들어지고, 그 상태를 아무도 확인하지 않는다.
 */
function toDays(raw: string | undefined): number {
  const n = Number(raw);
  return RANGES.some((r) => r.days === n) ? n : DEFAULT_DAYS;
}
