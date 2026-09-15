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
  POLICY_MODE_LABEL,
  type MetalType,
  type PriceSide,
} from '@/types/price';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 시세 변경 이력.
 *
 * 수정도 삭제도 없는 기록을 그대로 보여준다. 과거 주문의 판매가가 어떤 시세에서 나왔는지
 * 증명하는 것이 목적이라, "지금 값"보다 "그때 값"이 중요하다. 마감 시각이 비어 있는 행이
 * 지금 쓰이는 값이다.
 *
 * <p>방식 칸에는 예전 기록의 "정액 보정" 같은 이름도 그대로 나온다. 그때 그렇게 계산됐다는
 * 사실이라 지금 방식으로 바꿔 적지 않는다.
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
      note="수정도 삭제도 없는 기록이다. 마감 시각이 비어 있는 행이 지금 쓰이는 값이다."
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
            <option value="SELL">살 때</option>
            <option value="BUY">팔 때</option>
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
                <th className={styles.num}>고시가</th>
                <th>방식</th>
                <th>적용 시작</th>
                <th>적용 마감</th>
                <th>고시자</th>
                <th>사유</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td data-label="#" className={`${styles.num} ${styles.muted}`}>{row.id}</td>
                  <td data-label="고시가" data-card-title className={`${styles.num} ${styles.strong}`}>
                    {formatPricePerGram(row.pricePerGram)}
                  </td>
                  <td data-label="방식">{POLICY_MODE_LABEL[row.policyMode] ?? row.policyMode}</td>
                  <td data-label="적용 시작">{formatDateTime(row.effectiveFrom)}</td>
                  <td data-label="적용 마감">
                    {row.effectiveTo == null ? (
                      <StatusBadge tone="gold">현재 적용중</StatusBadge>
                    ) : (
                      <span className={styles.muted}>{formatDateTime(row.effectiveTo)}</span>
                    )}
                  </td>
                  <td data-label="고시자" className={styles.muted}>{row.createdBy}</td>
                  <td data-label="사유" className={`${styles.muted} ${styles.wrapText}`}>
                    {row.changeReason}
                  </td>
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
