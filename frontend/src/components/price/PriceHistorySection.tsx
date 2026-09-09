import Link from 'next/link';

import { PriceChart } from '@/components/price/PriceChart';
import { PriceHistoryTable, type HistoryRow } from '@/components/price/PriceHistoryTable';
import { SectionHead } from '@/components/ui/SectionHead';
import { METAL_LABEL, METAL_ORDER, type MetalType, type PriceHistoryPoint } from '@/types/price';
import styles from './PriceHistorySection.module.css';

/** 기간 선택지. 레퍼런스는 5개월부터 시작하는데, 오늘 사려는 사람이 궁금한 건 지난주다. */
export const RANGES = [
  { days: 7, label: '1주' },
  { days: 30, label: '1개월' },
  { days: 90, label: '3개월' },
  { days: 365, label: '1년' },
] as const;

export const DEFAULT_DAYS = 30;
export const DEFAULT_METAL: MetalType = 'GOLD_24K';

/** 표에 한 번에 보여줄 줄 수. 넘으면 접고 "더 보기"로 편다. */
const TABLE_ROWS = 20;

interface Props {
  metal: MetalType;
  days: number;
  /** 선택한 금속·기간의 판매(살 때) 이력. 오래된 순 */
  sell: PriceHistoryPoint[];
  /** 같은 조건의 매입(팔 때) 이력 */
  buy: PriceHistoryPoint[];
  /** 표를 다 펼친 상태인가 */
  expanded: boolean;
}

/**
 * 시세 이력 — 기간별 그래프와 고시 내역.
 *
 * <h2>서버 컴포넌트다</h2>
 * 금속과 기간이 <b>URL 에 있다.</b> 그래서 그래프와 표가 서버에서 그려져 초기 HTML 에
 * 실리고, 자바스크립트 없이도 동작하며, "순금 1년치" 화면을 링크로 보낼 수 있다.
 *
 * <p>클릭할 때마다 페이지가 다시 그려지는 대신 얻는 것이다. 상품 목록의 카테고리 탭을
 * 버튼이 아니라 {@code <Link>} 로 둔 것과 같은 판단이다.
 *
 * <h2>레퍼런스와 다르게 만든 것</h2>
 * 한국금거래소는 날짜 두 개를 고르고 <b>검색을 눌러야</b> 값이 나온다. 답을 보기 전에
 * 두 단계를 요구하는 셈이라, 여기서는 기간을 눌러 둔 상태로 시작한다(1개월).
 */
export function PriceHistorySection({ metal, days, sell, buy, expanded }: Props) {
  const rangeLabel = RANGES.find((r) => r.days === days)?.label ?? `${days}일`;
  const metalName = METAL_LABEL[metal];

  const rows = toRows(sell, buy);
  const shown = expanded ? rows : rows.slice(0, TABLE_ROWS);
  const hasMore = rows.length > TABLE_ROWS;

  return (
    <section className={styles.section} aria-labelledby="price-history">
      <div className="wrap">
        <SectionHead
          title="시세 추이"
          desc="당사가 고시한 시각마다 기록된 실제 거래 기준가입니다"
        />

        {/* 금속 선택. 위 시세 보드의 호버는 큰 카드만 바꾸고, 그래프는 여기서 고른다 */}
        <nav className={styles.chips} aria-label="금속 선택">
          {METAL_ORDER.map((m) => (
            <Link
              key={m}
              href={hrefFor(m, days)}
              className={`${styles.chip} ${m === metal ? styles.chipOn : ''}`}
              aria-current={m === metal ? 'page' : undefined}
              scroll={false}
            >
              {METAL_LABEL[m]}
            </Link>
          ))}
        </nav>

        <nav className={styles.chips} aria-label="기간 선택">
          {RANGES.map((r) => (
            <Link
              key={r.days}
              href={hrefFor(metal, r.days)}
              className={`${styles.chip} ${r.days === days ? styles.chipOn : ''}`}
              aria-current={r.days === days ? 'page' : undefined}
              scroll={false}
            >
              {r.label}
            </Link>
          ))}
        </nav>

        <div className={styles.chartBox}>
          <div className={styles.chartHead}>
            <h3 id="price-history" className={styles.chartTitle}>
              {metalName} · 내가 살 때
            </h3>
            <span className={styles.chartNote}>최근 {rangeLabel} · 원/g</span>
          </div>
          <PriceChart points={sell} label={`${metalName} 내가 살 때 최근 ${rangeLabel} 추이`} />
        </div>

        <div className={styles.tableBox}>
          <PriceHistoryTable rows={shown} metalName={metalName} />

          {hasMore && !expanded && (
            <Link href={hrefFor(metal, days, true)} className={styles.more} scroll={false}>
              전체 {rows.length}건 보기
            </Link>
          )}
        </div>

        <p className={styles.notes}>
          <span>· 표시 금액은 1돈(3.75g) 기준입니다. 그래프의 세로축은 1g 기준입니다.</span>
          <span>· 변동폭은 <b>직전 고시가</b>와 비교한 값입니다.</span>
        </p>
      </div>
    </section>
  );
}

/** 기본값이면 쿼리를 붙이지 않는다 — 같은 화면에 주소가 둘이면 검색엔진이 중복으로 본다. */
function hrefFor(metal: MetalType, days: number, all = false): string {
  const q = new URLSearchParams();
  if (metal !== DEFAULT_METAL) q.set('metal', metal);
  if (days !== DEFAULT_DAYS) q.set('days', String(days));
  if (all) q.set('all', '1');

  const s = q.toString();
  return s ? `/price?${s}#price-history` : '/price#price-history';
}

/**
 * 살 때·팔 때 이력을 <b>한 줄로</b> 짝짓는다.
 *
 * <h2>왜 시각이 정확히 같지 않은가</h2>
 * 지금은 두 값이 한 번의 고시에서 같은 시각으로 발행된다. 그런데 그 전에 쌓인 이력은
 * 한쪽씩 고시하던 때의 것이라 몇 초씩 어긋나 있다. 정확히 일치하는 시각만 묶으면
 * 그 데이터가 <b>각각 한쪽이 빈 두 줄</b>로 나온다 — 실제로 그렇게 나왔다.
 *
 * <p>그래서 {@link PAIR_WINDOW_MS} 안에 들어온 반대쪽을 같은 고시로 본다. 사람이 두
 * 입력을 연달아 넣는 데 걸리는 시간이고, 그보다 짧은 간격으로 서로 다른 고시가 두 번
 * 일어나는 일은 없다.
 *
 * <p>최신순으로 돌려준다. 이력은 위에서부터 읽는 것이 최근이다.
 */
const PAIR_WINDOW_MS = 3 * 60 * 1000;

function toRows(sell: PriceHistoryPoint[], buy: PriceHistoryPoint[]): HistoryRow[] {
  // 판매 이력을 뼈대로 삼고, 그 시각 근처의 매입값을 끌어와 붙인다.
  // 판매가 이 사이트의 주력이라 빠지는 시점이 거의 없다.
  const asc = <T extends { effectiveFrom: string }>(a: T, b: T) =>
    a.effectiveFrom.localeCompare(b.effectiveFrom);

  const sells = [...sell].sort(asc);
  const buys = [...buy].sort(asc);
  const usedBuy = new Set<number>();

  /** at 에 가장 가까운(창 안의) 매입 이력을 찾아 소비한다. 한 번 쓴 것은 다시 쓰지 않는다. */
  const takeBuyNear = (at: string): PriceHistoryPoint | null => {
    const t = Date.parse(at);
    let best: number | null = null;
    let bestGap = Number.POSITIVE_INFINITY;
    for (let i = 0; i < buys.length; i += 1) {
      if (usedBuy.has(i)) continue;
      const gap = Math.abs(Date.parse(buys[i].effectiveFrom) - t);
      if (gap <= PAIR_WINDOW_MS && gap < bestGap) {
        best = i;
        bestGap = gap;
      }
    }
    if (best == null) return null;
    usedBuy.add(best);
    return buys[best];
  };

  // 변동은 오래된 순으로 훑어야 "직전"이 정해진다.
  // 살 때와 팔 때를 각각 따로 잰다 — 두 값은 같이 움직이지만 폭이 다르다.
  let prevSell: number | null = null;
  let prevBuy: number | null = null;
  const rows: HistoryRow[] = sells.map((s) => {
    const b = takeBuyNear(s.effectiveFrom);
    const change = prevSell != null ? s.pricePerDon - prevSell : null;
    const buyChange = b && prevBuy != null ? b.pricePerDon - prevBuy : null;
    prevSell = s.pricePerDon;
    if (b) prevBuy = b.pricePerDon;

    return {
      at: s.effectiveFrom,
      sellPerDon: s.pricePerDon,
      buyPerDon: b?.pricePerDon ?? null,
      changePerDon: change,
      buyChangePerDon: buyChange,
    };
  });

  // 짝을 찾지 못한 매입 이력은 버리지 않는다. 매입만 고시한 시점이 표에서
  // 통째로 사라지면, 그날 매입가가 얼마였는지 되짚을 방법이 없어진다.
  buys.forEach((b, i) => {
    if (usedBuy.has(i)) return;
    rows.push({
      at: b.effectiveFrom,
      sellPerDon: null,
      buyPerDon: b.pricePerDon,
      changePerDon: null,
      buyChangePerDon: null,
    });
  });

  rows.sort((a, b) => a.at.localeCompare(b.at));
  return rows.reverse();
}
