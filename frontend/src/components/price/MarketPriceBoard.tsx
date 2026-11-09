'use client';

import { useState } from 'react';
import type { MetalRow } from '@/components/price/metalRows';
import { Sparkline } from '@/components/price/Sparkline';
import {
  directionOf,
  METAL_LABEL,
  type MarketPrice,
  type MetalType,
} from '@/types/price';
import {
  formatDeltaWithPercent,
  formatKrw,
  PRICE_INQUIRY_LABEL,
} from '@/utils/format';
import styles from './MarketPriceBoard.module.css';

interface Props {
  rows: MetalRow[];
  /** 시세표에 실린 값 중 가장 최근 기준시각. 없으면 null */
  quotedAt: string | null;
}

/** 주력 금속. 이 값이 기본으로 큰 카드에 뜬다. */
const PRIMARY: MetalType = 'GOLD_24K';

/** 금속별 순도 표기. 계산에는 쓰지 않는다 — 표시 전용이다 (개발계획 §9.2). */
const PURITY: Record<MetalType, string> = {
  GOLD_24K: '999.9',
  GOLD_18K: '750',
  GOLD_14K: '585',
  SILVER: '999',
  PLATINUM: '999.5',
};

/**
 * 메인 시세 보드.
 *
 * <pre>
 * ┌ 주력 카드 (기본 24K) ─────────┐ ┌ 18K ┐ ┌ 14K ┐
 * │ 내가 살 때 / 내가 팔 때        │ └─────┘ └─────┘
 * │ 추이 그래프                    │ ┌ 은  ┐ ┌ 백금 ┐
 * └───────────────────────────────┘ └─────┘ └─────┘
 * ┌ 전체 시세표 (5종 × 매수·매도) ─────────────────┐
 * </pre>
 *
 * <h2>마우스를 올리면 카드 전체가 따라간다</h2>
 * 오른쪽 금속 카드나 아래 표의 줄에 포인터를 올리면 왼쪽 카드가 그 금속으로 바뀐다.
 * <b>그래프만 바꾸지 않는 이유</b>는 그래프가 "순금 24K" 카드 안에 있기 때문이다.
 * 제목과 금액은 24K 인데 그래프만 18K 이면 어느 쪽을 봐야 할지 알 수 없다.
 *
 * <h2>클라이언트 컴포넌트인 범위</h2>
 * 호버 상태 하나 때문에 이 컴포넌트만 클라이언트다. 값은 전부 서버가 넘겨준
 * props 라서 <b>초기 HTML 에 시세가 그대로 실린다</b> — SEO 가 이 프로젝트의
 * 첫 번째 요구사항이다 (설계서 §1).
 *
 * <h2>금액을 계산하지 않는다</h2>
 * 단가·변동액·변동률 전부 서버가 준 값을 포맷만 한다 (설계서 원칙 P1).
 */
export function MarketPriceBoard({ rows, quotedAt }: Props) {
  const [hovered, setHovered] = useState<MetalType | null>(null);

  const byMetal = new Map(rows.map((r) => [r.metalType, r]));
  const active = byMetal.get(hovered ?? PRIMARY) ?? rows[0];
  if (!active) return null;

  const sideRows = rows.filter((r) => r.metalType !== PRIMARY);

  return (
    <section className={styles.board} aria-labelledby="today-price">
      <header className={styles.head}>
        <h2 id="today-price" className={styles.title}>
          오늘의 고시 시세
        </h2>
        <p className={styles.meta}>
          {quotedAt ? `${quotedAt} 기준` : '고시 대기'} · 1돈(3.75g)
        </p>
      </header>

      <div className={styles.grid}>
        <FeatureCard row={active} />

        <div className={styles.sideCards}>
          {sideRows.map((row) => (
            <SideCard
              key={row.metalType}
              row={row}
              active={hovered === row.metalType}
              onEnter={() => setHovered(row.metalType)}
              onLeave={() => setHovered(null)}
            />
          ))}
        </div>
      </div>

      <PriceTable
        rows={rows}
        activeMetal={active.metalType}
        onEnter={setHovered}
        onLeave={() => setHovered(null)}
      />

      <p className={styles.notes}>
        <span>· 시세는 당사 기준이며, 시장 상황에 따라 변동될 수 있습니다.</span>
        <span>· 최종 결제 금액은 주문하시는 시각의 고시 시세로 확정됩니다.</span>
      </p>
    </section>
  );
}

/* ------------------------------------------------------------------ */

function FeatureCard({ row }: { row: MetalRow }) {
  const values = row.history.map((h) => h.pricePerGram);

  return (
    <article className={styles.feature}>
      <div className={styles.featureHead}>
        <h3 className={styles.metalName}>{METAL_LABEL[row.metalType]}</h3>
        <span className={styles.purity}>{PURITY[row.metalType]}</span>
      </div>

      <div className={styles.sides}>
        <PriceSide label="내가 살 때" price={row.sell} emphasis />
        <div className={styles.divider} aria-hidden="true" />
        <PriceSide label="내가 팔 때" price={row.buy} />
      </div>

      <div className={styles.chartBox}>
        <div className={styles.chartHead}>
          <span>{METAL_LABEL[row.metalType]} · 최근 30일 추이</span>
        </div>
        {/* 금속마다 그라디언트 id 가 달라야 서로 덮어쓰지 않는다 */}
        <Sparkline values={values} gradientId={`spark-${row.metalType}`} />
      </div>
    </article>
  );
}

function PriceSide({
  label,
  price,
  emphasis = false,
}: {
  label: string;
  price: MarketPrice | null;
  emphasis?: boolean;
}) {
  const available = price?.available === true && price.pricePerDon != null;
  const dir = directionOf(price?.change);

  return (
    <div className={styles.side}>
      <p className={styles.sideLabel}>{label}</p>
      {available ? (
        <p className={`${styles.amount} tabular ${emphasis ? '' : styles.amountMuted}`}>
          {formatKrw(price.pricePerDon)}
        </p>
      ) : (
        <p className={styles.inquiry}>{price?.displayText ?? PRICE_INQUIRY_LABEL}</p>
      )}
      {price?.change ? (
        <p className={`${styles.delta} tabular ${styles[dir.toLowerCase()]}`}>
          {formatDeltaWithPercent(price.change.changePerDon, price.change.changePercent)}
        </p>
      ) : (
        <p className={`${styles.delta} ${styles.flat}`}>{available ? '최초 고시' : ''}</p>
      )}
    </div>
  );
}

function SideCard({
  row,
  active,
  onEnter,
  onLeave,
}: {
  row: MetalRow;
  active: boolean;
  onEnter: () => void;
  onLeave: () => void;
}) {
  const sell = row.sell;
  const available = sell?.available === true && sell.pricePerDon != null;
  const dir = directionOf(sell?.change);

  return (
    // 마우스 전용 기능이 아니다 — 키보드 포커스로도 같은 전환이 일어난다
    <button
      type="button"
      className={`${styles.sideCard} ${active ? styles.sideCardOn : ''}`}
      onMouseEnter={onEnter}
      onMouseLeave={onLeave}
      onFocus={onEnter}
      onBlur={onLeave}
      aria-pressed={active}
    >
      <span className={styles.sideCardName}>{METAL_LABEL[row.metalType]}</span>
      <span className={`${styles.sideCardPrice} tabular`}>
        {available ? formatKrw(sell.pricePerDon) : PRICE_INQUIRY_LABEL}
      </span>
      <span className={`${styles.sideCardDelta} tabular ${styles[dir.toLowerCase()]}`}>
        {sell?.change
          ? formatDeltaWithPercent(sell.change.changePerDon, sell.change.changePercent)
          : ''}
      </span>
    </button>
  );
}

function PriceTable({
  rows,
  activeMetal,
  onEnter,
  onLeave,
}: {
  rows: MetalRow[];
  activeMetal: MetalType;
  onEnter: (m: MetalType) => void;
  onLeave: () => void;
}) {
  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <caption className="sr-only">
          오늘의 고시 시세 — 금속별 매수가와 매도가. 줄에 마우스를 올리면 위 카드가 해당
          금속으로 바뀝니다.
        </caption>
        <thead>
          <tr>
            <th scope="col">항목</th>
            <th scope="col" className={styles.numCol}>
              내가 살 때
            </th>
            <th scope="col" className={styles.numCol}>
              내가 팔 때
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.metalType}
              className={row.metalType === activeMetal ? styles.rowOn : ''}
              onMouseEnter={() => onEnter(row.metalType)}
              onMouseLeave={onLeave}
            >
              <th scope="row" className={styles.rowName}>
                {METAL_LABEL[row.metalType]}
                {row.metalType === PRIMARY && (
                  <span className={styles.rowPurity}>{PURITY[row.metalType]}</span>
                )}
              </th>
              <PriceCells price={row.sell} />
              <PriceCells price={row.buy} />
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * 금액 칸. 변동을 <b>금액 바로 옆</b>에 작게 붙인다.
 *
 * <p>변동을 별도 열로 두면 열이 다섯이 되어 좁은 화면에서 숫자끼리 붙고, 무엇보다
 * "이 변동이 어느 값의 변동인가"를 눈이 한 칸 건너가서 맞춰야 한다. 값 옆에 붙이면
 * 살 때가 얼마 올랐고 팔 때가 얼마 내렸는지가 한눈에 들어온다.
 */
function PriceCells({ price }: { price: MarketPrice | null }) {
  const available = price?.available === true && price.pricePerDon != null;
  const dir = directionOf(price?.change);

  return (
    <td className={`${styles.numCol} tabular ${available ? styles.cellPrice : styles.cellText}`}>
      <span className={styles.priceWithDelta}>
        <span>
          {available ? formatKrw(price.pricePerDon) : (price?.displayText ?? PRICE_INQUIRY_LABEL)}
        </span>
        {price?.change && (
          <small className={`${styles.deltaBeside} ${styles[dir.toLowerCase()]}`}>
            {formatDeltaWithPercent(price.change.changePerDon, price.change.changePercent)}
          </small>
        )}
      </span>
    </td>
  );
}
