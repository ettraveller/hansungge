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
 * <p>화면에 "시세문의"가 나가는 상태는 <b>오류가 아니라 안전장치가 작동한 것</b>이다.
 * 다만 관리자에게는 그 사실이 <b>사람 말로</b> 보여야 한다. "노후"나 "STALE" 같은
 * 말은 내부 용어이고, 그 말을 읽은 사람은 시스템이 고장 났다고 생각한다.
 * 실제로 벌어진 일은 "고시한 지 오래돼서 값을 내리지 않고 있다"이다.
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
          <Alert
            tone="warning"
            title={`${unusable.length}건이 지금 쇼핑몰에서 "시세문의"로 나가고 있습니다`}
          >
            금액 대신 &quot;시세문의&quot;가 표시되고 주문이 막힙니다. 값이 틀린 것이 아니라,
            <strong> 마지막 고시로부터 시간이 너무 지나 값을 내걸지 않고 있는 것</strong>입니다.
            오래된 시세로 금을 팔면 손해가 나기 때문입니다.
            <br />
            아래에서 새로 고시하면 즉시 풀립니다. 매번 손으로 고시하지 않으려면
            보정 정책에서 <strong>자동고시</strong>를 켜 두세요.
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
      <td data-label="금속" data-card-title className={styles.strong}>{METAL_LABEL[row.metalType] ?? row.metalName}</td>
      <td data-label="구분">{row.priceSide === 'SELL' ? '판매' : '매입'}</td>
      <td data-label="원본" className={styles.num}>{formatPricePerGram(row.rawPricePerGram)}</td>
      <td data-label="보정" className={styles.num}>{formatAdjustment(row.adjustment)}</td>
      <td data-label="최종" className={`${styles.num} ${styles.strong}`}>
        {row.available ? formatPricePerGram(row.pricePerGram) : '-'}
      </td>
      <td data-label="정책">{row.policyMode ? MODE_LABEL[row.policyMode] : '-'}</td>
      <td data-label="상태">
        <AvailabilityBadge row={row} />
      </td>
      <td data-label="고시 시각" className={styles.muted}>{formatDateTime(row.effectiveFrom)}</td>
      <td data-label="고시자" className={`${styles.muted} ${styles.wrapText}`}>
        {row.createdBy ? `${row.createdBy} · ${row.changeReason ?? '-'}` : '-'}
      </td>
    </tr>
  );
}

/**
 * 상태 배지.
 *
 * 내부 값(AVAILABLE / STALE / NONE)을 그대로 옮겨 적지 않는다. "노후"는 이 표를 보는
 * 사람의 말이 아니고, 읽는 순간 고장으로 오해된다. 무슨 일이 벌어졌는지를 적는다.
 */
function AvailabilityBadge({ row }: { row: AdminCurrentPrice }) {
  if (row.availability === 'AVAILABLE') return <StatusBadge tone="success">판매중</StatusBadge>;
  if (row.availability === 'STALE') {
    return <StatusBadge tone="warning">고시한 지 오래됨</StatusBadge>;
  }
  return <StatusBadge tone="neutral">고시 안 됨</StatusBadge>;
}
