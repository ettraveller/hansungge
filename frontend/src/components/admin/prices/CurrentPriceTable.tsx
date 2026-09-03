'use client';

import { Alert } from '../Alert';
import { Panel } from '../Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useCurrentPrices } from '@/hooks/admin/usePrices';
import { formatAdjustment, formatDateTime, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL, type PricePolicyMode } from '@/types/price';
import type { AdminCurrentPrice } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const MODE_LABEL: Record<PricePolicyMode, string> = {
  AUTO: '보정 없음',
  FIXED_ADJ: '정액',
  PERCENT_ADJ: '정률',
  MANUAL: '지정가',
};

/**
 * 현재 시세 현황.
 *
 * 원본 → 보정 → 최종 3단으로 나눠 보여준다. 한 칸에 최종가만 있으면 정책이 잘못 걸려
 * 있어도 알아채지 못한다.
 *
 * <p>노후(STALE)·없음(NONE)은 <b>오류가 아니라 안전장치가 작동한 상태</b>다. 이때 쇼핑몰은
 * 금액 대신 "시세문의"를 노출하고 주문을 막는다 — 그 사실을 관리자가 바로 알아야 한다.
 */
export function CurrentPriceTable() {
  const { data, isLoading, error } = useCurrentPrices();

  const rows = data ?? [];
  const unusable = rows.filter((r) => !r.available);

  return (
    <Panel
      title="현재 시세"
      note="원본 시세에 보정 정책을 적용한 값이 최종가다. 쇼핑몰은 이 최종가로 계산한다."
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '시세를 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {unusable.length > 0 && (
        <div className={styles.panelBody}>
          <Alert tone="warning" title={`${unusable.length}건이 판매에 쓸 수 없는 상태입니다`}>
            아래 표에서 <strong>시세문의</strong> 로 표시된 조합은 쇼핑몰에서 금액 대신
            &quot;시세문의&quot;가 나가고 주문이 막힌다. 새로 고시하면 즉시 풀린다.
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
                <th className={styles.num}>원본</th>
                <th className={styles.num}>보정</th>
                <th className={styles.num}>최종</th>
                <th>정책</th>
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
      <td className={styles.strong}>{METAL_LABEL[row.metalType] ?? row.metalName}</td>
      <td>{row.priceSide === 'SELL' ? '판매' : '매입'}</td>
      <td className={styles.num}>{formatPricePerGram(row.rawPricePerGram)}</td>
      <td className={styles.num}>{formatAdjustment(row.adjustment)}</td>
      <td className={`${styles.num} ${styles.strong}`}>
        {row.available ? formatPricePerGram(row.pricePerGram) : '-'}
      </td>
      <td>{row.policyMode ? MODE_LABEL[row.policyMode] : '-'}</td>
      <td>
        <AvailabilityBadge row={row} />
      </td>
      <td className={styles.muted}>{formatDateTime(row.effectiveFrom)}</td>
      <td className={`${styles.muted} ${styles.wrapText}`}>
        {row.createdBy ? `${row.createdBy} · ${row.changeReason ?? '-'}` : '-'}
      </td>
    </tr>
  );
}

function AvailabilityBadge({ row }: { row: AdminCurrentPrice }) {
  if (row.availability === 'AVAILABLE') return <StatusBadge tone="success">정상</StatusBadge>;
  if (row.availability === 'STALE') {
    return <StatusBadge tone="warning">노후 · 시세문의</StatusBadge>;
  }
  return <StatusBadge tone="neutral">미고시 · 시세문의</StatusBadge>;
}
