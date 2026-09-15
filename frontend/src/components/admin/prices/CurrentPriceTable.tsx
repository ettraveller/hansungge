'use client';

import { Alert } from '../Alert';
import { Panel } from '../Panel';
import { deltaClass, sideLabel } from './QuoteChangeTable';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useCurrentPrices } from '@/hooks/admin/usePrices';
import { formatDateTime, formatDeltaWithPercent, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL, POLICY_MODE_LABEL } from '@/types/price';
import type { AdminCurrentPrice } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 현재 시세 현황.
 *
 * 쇼핑몰이 지금 이 값으로 상품 가격을 계산한다. 한 번 고시한 값은 새로 고시할 때까지
 * 그대로 쓰인다 — 시간이 지났다고 내려가지 않는다.
 *
 * <p>변동은 금액 바로 옆에 작게 붙인다. 따로 칸을 두면 눈이 금액과 변동 사이를 오가야 하고,
 * 방금 고시한 값이 자릿수를 잘못 친 것인지 한눈에 들어오지 않는다.
 *
 * <p>"시세문의"로 나가는 줄은 이제 <b>아직 한 번도 고시하지 않은</b> 금속뿐이다.
 */
export function CurrentPriceTable() {
  const { data, isLoading, error } = useCurrentPrices();

  const rows = data ?? [];
  const unquoted = rows.filter((r) => !r.available);

  return (
    <Panel
      title="현재 시세"
      note="쇼핑몰은 이 값으로 상품 가격을 계산한다. 새로 고시할 때까지 이 값이 그대로 쓰인다."
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '시세를 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {unquoted.length > 0 && (
        <div className={styles.panelBody}>
          <Alert tone="warning" title={`${unquoted.length}건이 아직 고시되지 않았습니다`}>
            {unquoted
              .map((r) => `${METAL_LABEL[r.metalType] ?? r.metalName} ${sideLabel(r.priceSide)}`)
              .join(', ')}
            {' — '}이 금속을 쓰는 상품은 금액 대신 &quot;시세문의&quot;가 나가고 주문이
            막힙니다. 고시하면 바로 풀립니다.
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className={styles.empty}>표시할 시세가 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>금속</th>
                <th>구분</th>
                <th className={styles.num}>고시가 · 변동</th>
                <th>방식</th>
                <th>상태</th>
                <th>고시 시각</th>
                <th>고시자 · 사유</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <PriceRow key={`${row.metalType}-${row.priceSide}`} row={row} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}

function PriceRow({ row }: { row: AdminCurrentPrice }) {
  return (
    <tr>
      <td data-label="금속" data-card-title className={styles.strong}>
        {METAL_LABEL[row.metalType] ?? row.metalName}
      </td>
      <td data-label="구분">{sideLabel(row.priceSide)}</td>
      <td data-label="고시가" className={styles.num}>
        {row.available ? (
          <>
            <span className={styles.strong}>{formatPricePerGram(row.pricePerGram)}</span>
            {row.change != null && (
              <span className={deltaClass(row.change.changePerGram)}>
                {formatDeltaWithPercent(row.change.changePerGram, row.change.changePercent)}
              </span>
            )}
          </>
        ) : (
          '-'
        )}
      </td>
      <td data-label="방식">{row.mode ? POLICY_MODE_LABEL[row.mode] : '-'}</td>
      <td data-label="상태">
        {row.available ? (
          <StatusBadge tone="success">판매중</StatusBadge>
        ) : (
          <StatusBadge tone="neutral">고시 안 됨</StatusBadge>
        )}
      </td>
      <td data-label="고시 시각" className={styles.muted}>
        {formatDateTime(row.effectiveFrom)}
      </td>
      <td data-label="고시자" className={`${styles.muted} ${styles.wrapText}`}>
        {row.createdBy ? `${row.createdBy} · ${row.changeReason ?? '-'}` : '-'}
      </td>
    </tr>
  );
}
