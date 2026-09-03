'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Pagination } from '../Pagination';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useApprovePending, usePendingPrices, useRejectPending } from '@/hooks/admin/usePrices';
import { formatDateTime, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL } from '@/types/price';
import type { PendingStatus, PricePending } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const STATUS_LABEL: Record<PendingStatus, string> = {
  PENDING: '대기',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
};

type Decision = { pending: PricePending; kind: 'approve' | 'reject' };

/**
 * 이상 변동 승인 대기.
 *
 * 여기 걸린 값은 <b>아직 적용되지 않았다.</b> 해당 금속의 현재 시세는 직전 값 그대로다 —
 * 즉 "승인 안 하면 시세가 멈춘다"가 아니라 "승인 안 하면 옛 시세로 계속 판다"이다.
 * 그래서 이 패널은 대기 건이 있을 때 눈에 띄어야 한다.
 */
export function PricePendingPanel({ canDecide }: { canDecide: boolean }) {
  const [status, setStatus] = useState<PendingStatus>('PENDING');
  const [page, setPage] = useState(0);
  const [decision, setDecision] = useState<Decision | null>(null);

  const { data, isLoading, error } = usePendingPrices(status, page);
  const approve = useApprovePending();
  const reject = useRejectPending();

  const active = decision?.kind === 'approve' ? approve : reject;

  const confirm = (reason: string) => {
    if (!decision) return;
    const args = { id: decision.pending.id, reason };
    const onSuccess = () => setDecision(null);
    if (decision.kind === 'approve') approve.mutate(args, { onSuccess });
    else reject.mutate(args, { onSuccess });
  };

  const rows = data?.content ?? [];
  const waiting = status === 'PENDING' ? rows.length : 0;

  return (
    <Panel
      title="승인 대기"
      note="대기 중인 값은 적용되지 않았다. 해당 금속은 직전 시세로 계속 판매된다."
      actions={
        <select
          className={styles.select}
          value={status}
          aria-label="상태 필터"
          onChange={(e) => {
            setStatus(e.target.value as PendingStatus);
            setPage(0);
          }}
        >
          <option value="PENDING">대기</option>
          <option value="APPROVED">승인됨</option>
          <option value="REJECTED">반려됨</option>
        </select>
      }
      flush
    >
      {(error || approve.error || reject.error) && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {[error, approve.error, reject.error]
              .filter(Boolean)
              .map((e) => (e instanceof ApiError ? e.message : '처리하지 못했습니다.'))
              .join(' / ')}
          </Alert>
        </div>
      )}

      {status === 'PENDING' && waiting > 0 && (
        <div className={styles.panelBody}>
          <Alert tone="warning" title={`승인을 기다리는 시세가 ${waiting}건 있습니다`}>
            변동률이 허용치를 넘어 자동 적용이 중단된 건이다. 국제 시세를 확인한 뒤
            승인하거나 반려해야 한다.
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className={styles.empty}>
          {status === 'PENDING' ? '승인을 기다리는 건이 없습니다.' : '해당 상태의 건이 없습니다.'}
        </p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.num}>#</th>
                <th>금속</th>
                <th>구분</th>
                <th className={styles.num}>현재 적용가</th>
                <th className={styles.num}>대기 중인 값</th>
                <th className={styles.num}>변동률</th>
                <th>상태</th>
                <th>등록 시각</th>
                <th>판단</th>
                {canDecide && <th aria-label="작업" />}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td className={`${styles.num} ${styles.muted}`}>{row.id}</td>
                  <td className={styles.strong}>{METAL_LABEL[row.metalType]}</td>
                  <td>{row.priceSide === 'SELL' ? '판매' : '매입'}</td>
                  <td className={styles.num}>{formatPricePerGram(row.prevPrice)}</td>
                  <td className={`${styles.num} ${styles.strong}`}>
                    {formatPricePerGram(row.calcPrice)}
                  </td>
                  <td className={styles.num}>
                    {row.changePercent == null
                      ? '-'
                      : `${row.changePercent > 0 ? '+' : ''}${row.changePercent}%`}
                  </td>
                  <td>
                    <StatusBadge
                      tone={
                        row.status === 'PENDING'
                          ? 'warning'
                          : row.status === 'APPROVED'
                            ? 'success'
                            : 'neutral'
                      }
                    >
                      {STATUS_LABEL[row.status]}
                    </StatusBadge>
                  </td>
                  <td className={styles.muted}>{formatDateTime(row.createdAt)}</td>
                  <td className={styles.muted}>
                    {row.decidedBy ? `${row.decidedBy} · ${formatDateTime(row.decidedAt)}` : '-'}
                  </td>
                  {canDecide && (
                    <td>
                      {row.status === 'PENDING' ? (
                        <div className={styles.rowActions}>
                          <Button
                            small
                            variant="gold"
                            onClick={() => setDecision({ pending: row, kind: 'approve' })}
                          >
                            승인
                          </Button>
                          <Button
                            small
                            variant="danger"
                            onClick={() => setDecision({ pending: row, kind: 'reject' })}
                          >
                            반려
                          </Button>
                        </div>
                      ) : (
                        <span className={styles.muted}>-</span>
                      )}
                    </td>
                  )}
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

      <ReasonDialog
        open={decision != null}
        danger={decision?.kind === 'reject'}
        title={decision?.kind === 'approve' ? '시세 승인' : '시세 반려'}
        confirmLabel={decision?.kind === 'approve' ? '승인하고 적용' : '반려'}
        maxLength={280}
        placeholder={
          decision?.kind === 'approve'
            ? '예: 국제 금값 급등 확인, 적용 승인'
            : '예: 수집 오류로 판단, 재수집 예정'
        }
        description={
          decision == null ? null : decision.kind === 'approve' ? (
            <>
              {METAL_LABEL[decision.pending.metalType]}{' '}
              {decision.pending.priceSide === 'SELL' ? '판매' : '매입'} 시세를{' '}
              <strong>{formatPricePerGram(decision.pending.calcPrice)}</strong> 로 즉시
              적용한다. 변동률 재검사는 하지 않는다 — 임계값 초과를 알고 승인하는 것이기
              때문이다.
            </>
          ) : (
            <>
              반려해도 현재 시세는 바뀌지 않는다. 판단했다는 사실만 감사로그에 남는다.
            </>
          )
        }
        pending={active?.isPending}
        error={
          active?.error instanceof ApiError ? active.error.message : null
        }
        onConfirm={confirm}
        onClose={() => setDecision(null)}
      />
    </Panel>
  );
}
