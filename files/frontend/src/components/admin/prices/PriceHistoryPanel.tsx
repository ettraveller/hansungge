'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Pagination } from '../Pagination';
import { Panel } from '../Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { usePriceHistory } from '@/hooks/admin/usePrices';
import { formatDateTime, formatPricePerGram } from '@/utils/format';
import {
  METAL_LABEL,
  METAL_ORDER,
  type MetalType,
  type PricePolicyMode,
  type PriceSide,
} from '@/types/price';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const MODE_LABEL: Record<PricePolicyMode, string> = {
  AUTO: '보정 없음',
  FIXED_ADJ: '정액',
  PERCENT_ADJ: '정률',
  MANUAL: '지정가',
};

/**
 * 시세 변경 이력.
 *
 * 이 표는 append-only 테이블을 그대로 보여준다 — 수정도 삭제도 없다. 과거 주문의
 * 판매가가 어떤 시세에서 나왔는지 증명하는 것이 목적이라, "지금 값"보다 "그때 값"이 중요하다.
 * {@code effectiveTo} 가 비어 있는 행이 현재 적용 중인 값이다.
 */
export function PriceHistoryPanel() {
  const [metal, setMetal] = useState<MetalType>('GOLD_24K');
  const [side, setSide] = useState<PriceSide>('SELL');
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = usePriceHistory(metal, side, page);
  const rows = data?.content ?? [];

  return (
    <Panel
      title="시세 변경 이력"
      note="수정도 삭제도 없는 기록이다. 마감 시각이 비어 있는 행이 현재 적용값이다."
      actions={
        <>
          <select
            className={styles.select}
            value={metal}
            aria-label="금속"
            onChange={(e) => {
              setMetal(e.target.value as MetalType);
              setPage(0);
            }}
          >
            {METAL_ORDER.map((m) => (
              <option key={m} value={m}>
                {METAL_LABEL[m]}
              </option>
            ))}
          </select>
          <select
            className={styles.select}
            value={side}
            aria-label="구분"
            onChange={(e) => {
              setSide(e.target.value as PriceSide);
              setPage(0);
            }}
          >
            <option value="SELL">판매</option>
            <option value="BUY">매입</option>
          </select>
        </>
      }
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '이력을 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className={styles.empty}>고시된 이력이 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.num}>#</th>
                <th className={styles.num}>최종 시세</th>
                <th>보정</th>
                <th>적용 시작</th>
                <th>적용 마감</th>
                <th>고시자</th>
                <th>사유</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td className={`${styles.num} ${styles.muted}`}>{row.id}</td>
                  <td className={`${styles.num} ${styles.strong}`}>
                    {formatPricePerGram(row.pricePerGram)}
                  </td>
                  <td>{MODE_LABEL[row.policyMode]}</td>
                  <td>{formatDateTime(row.effectiveFrom)}</td>
                  <td>
                    {row.effectiveTo == null ? (
                      <StatusBadge tone="gold">현재 적용중</StatusBadge>
                    ) : (
                      <span className={styles.muted}>{formatDateTime(row.effectiveTo)}</span>
                    )}
                  </td>
                  <td className={styles.muted}>{row.createdBy}</td>
                  <td className={`${styles.muted} ${styles.wrapText}`}>{row.changeReason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onChange={setPage}
        />
      )}
    </Panel>
  );
}
