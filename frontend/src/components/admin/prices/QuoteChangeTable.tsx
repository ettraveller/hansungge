'use client';

import { formatPercent, formatPricePerDon } from '@/utils/format';
import { METAL_LABEL, type PriceSide } from '@/types/price';
import type { QuoteLine } from '@/types/admin';
import styles from '../admin.module.css';

export function sideLabel(side: PriceSide): string {
  return side === 'SELL' ? '살 때' : '팔 때';
}

/** 오르면 붉게, 내리면 푸르게. 국내 시세표 관행이다. */
export function deltaClass(value: number | null | undefined): string {
  if (value == null || value === 0) return styles.priceDelta;
  return `${styles.priceDelta} ${value > 0 ? styles.deltaUp : styles.deltaDown}`;
}

/**
 * 고시로 바뀌는 값 표.
 *
 * 확인창과 고시 결과가 같은 표를 쓴다. 두 곳이 따로 그리면 "확인할 때 본 값"과
 * "나간 값"을 서로 다른 말로 보여주게 된다.
 *
 * 단위는 고시 입력과 같은 원/돈이다. 입력은 돈인데 표가 g 면 방금 친 숫자와 달라서
 * 맞게 들어갔는지 확인할 수 없다. 돈 값도 서버가 계산해서 준다 — 18K·14K 환산값까지
 * 화면에서 곱하지 않는다 (원칙 P1).
 */
export function QuoteChangeTable({ lines }: { lines: QuoteLine[] }) {
  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>금속</th>
            <th>구분</th>
            <th className={styles.num}>지금</th>
            <th className={styles.num}>바뀔 값</th>
            <th className={styles.num}>변동</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr
              key={`${line.metalType}-${line.priceSide}`}
              className={line.exceedsLimit ? styles.rowWarn : undefined}
            >
              <td data-label="금속" data-card-title className={styles.strong}>
                {METAL_LABEL[line.metalType] ?? line.metalName}
                {line.mode === 'DERIVED' && line.deriveRatio != null && (
                  <span className={styles.cellSub}>24K × {line.deriveRatio}</span>
                )}
              </td>
              <td data-label="구분">{sideLabel(line.priceSide)}</td>
              <td data-label="지금" className={`${styles.num} ${styles.muted}`}>
                {line.currentPricePerDon == null
                  ? '처음 고시'
                  : formatPricePerDon(line.currentPricePerDon)}
              </td>
              <td data-label="바뀔 값" className={`${styles.num} ${styles.strong}`}>
                {formatPricePerDon(line.newPricePerDon)}
              </td>
              <td data-label="변동" className={styles.num}>
                {line.changePercent == null ? (
                  '-'
                ) : (
                  <span className={deltaClass(line.changePercent)}>
                    {formatPercent(line.changePercent)}
                  </span>
                )}
                {line.exceedsLimit && <span className={styles.cellSub}>확인 필요</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
