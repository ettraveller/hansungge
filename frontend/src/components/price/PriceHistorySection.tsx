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
 * 살 때·팔 때 이력을 같은 시각끼리 짝짓는다.
 *
 * <p>두 값은 한 번의 고시에서 함께 정해지므로 {@code effectiveFrom} 이 같다. 그래도
 * 한쪽만 있는 시점이 나올 수 있어서(매입가를 고시하지 않은 금속 등) 합집합으로 모은다 —
 * 교집합만 쓰면 그 시점이 표에서 통째로 사라진다.
 *
 * <p>최신순으로 돌려준다. 이력은 위에서부터 읽는 것이 최근이다.
 */
function toRows(sell: PriceHistoryPoint[], buy: PriceHistoryPoint[]): HistoryRow[] {
  const buyAt = new Map(buy.map((p) => [p.effectiveFrom, p]));
  const times = [...new Set([...sell.map((p) => p.effectiveFrom), ...buy.map((p) => p.effectiveFrom)])];
  times.sort();

  const sellAt = new Map(sell.map((p) => [p.effectiveFrom, p]));

  // 변동은 오래된 순으로 훑어야 "직전"이 정해진다
  let prevSell: number | null = null;
  const rows: HistoryRow[] = times.map((at) => {
    const s = sellAt.get(at) ?? null;
    const b = buyAt.get(at) ?? null;
    const change = s && prevSell != null ? s.pricePerDon - prevSell : null;
    if (s) prevSell = s.pricePerDon;

    return {
      at,
      sellPerDon: s?.pricePerDon ?? null,
      buyPerDon: b?.pricePerDon ?? null,
      changePerDon: change,
    };
  });

  return rows.reverse();
}
