'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Pagination } from '../Pagination';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  useApprovePendingBatch,
  usePendingPrices,
  useRejectPendingBatch,
} from '@/hooks/admin/usePrices';
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

/**
 * 한 번의 고시에서 나온 대기 건들을 한 줄로 묶은 것.
 *
 * <p>살 때·팔 때는 함께 정해지는 값이라 화면에서도 함께 보여야 하고, 함께 판단해야
 * 한다. 한 건씩 승인하면 그 사이에 <b>한쪽만 새 값인 시세표</b>가 고객에게 걸린다.
 */
interface PendingGroup {
  key: string;
  metalType: PricePending['metalType'];
  status: PendingStatus;
  createdAt: string;
  decidedBy?: string | null;
  decidedAt?: string | null;
  sell: PricePending | null;
  buy: PricePending | null;
  /** 함께 판단할 대상. 보통 둘, 한쪽만 걸렸으면 하나다. */
  ids: number[];
}

/** 사람이 두 값을 연달아 넣는 데 걸리는 시간. 그 안이면 같은 고시로 본다. */
const PAIR_WINDOW_MS = 3 * 60 * 1000;

/**
 * 금속 + 상태 + 등록 시각이 가까운 것끼리 묶는다.
 *
 * <p>정확히 같은 시각만 묶지 않는 이유 — 한쪽씩 고시하던 때에 쌓인 건들은 몇 초씩
 * 어긋나 있다. 그것들이 각각 한 줄로 나오면 지금 고치려는 화면이 그대로 남는다.
 */
function groupPendings(rows: PricePending[]): PendingGroup[] {
  const groups: PendingGroup[] = [];

  for (const row of rows) {
    const at = Date.parse(row.createdAt);
    const slot = groups.find(
      (g) =>
        g.metalType === row.metalType &&
        g.status === row.status &&
        Math.abs(Date.parse(g.createdAt) - at) <= PAIR_WINDOW_MS &&
        (row.priceSide === 'SELL' ? g.sell === null : g.buy === null),
    );

    const target =
      slot ??
      (() => {
        const created: PendingGroup = {
          key: `${row.metalType}-${row.status}-${row.id}`,
          metalType: row.metalType,
          status: row.status,
          createdAt: row.createdAt,
          decidedBy: row.decidedBy,
          decidedAt: row.decidedAt,
          sell: null,
          buy: null,
          ids: [],
        };
        groups.push(created);
        return created;
      })();

    if (row.priceSide === 'SELL') target.sell = row;
    else target.buy = row;
    target.ids.push(row.id);
  }

  return groups;
}

type Decision = { group: PendingGroup; kind: 'approve' | 'reject' };

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
  const approve = useApprovePendingBatch();
  const reject = useRejectPendingBatch();

  const active = decision?.kind === 'approve' ? approve : reject;

  const confirm = (reason: string) => {
    if (!decision) return;
    // 짝을 함께 넘긴다. 서버가 한 트랜잭션에서 처리하므로 하나라도 실패하면
    // 전부 되돌아간다 — 한쪽만 적용된 시세표가 남지 않는다.
    const args = { ids: decision.group.ids, reason };
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
                <th>금속</th>
                <th className={styles.num}>내가 살 때</th>
                <th className={styles.num}>내가 팔 때</th>
                <th>상태</th>
                <th>등록 시각</th>
                <th>판단</th>
                {canDecide && <th aria-label="작업" />}
              </tr>
            </thead>
            <tbody>
              {groupPendings(rows).map((g) => (
                <tr key={g.key}>
                  <td data-label="금속" data-card-title className={styles.strong}>
                    {METAL_LABEL[g.metalType]}
                  </td>
                  <td data-label="내가 살 때" className={styles.num}>
                    <SideCell row={g.sell} />
                  </td>
                  <td data-label="내가 팔 때" className={styles.num}>
                    <SideCell row={g.buy} />
                  </td>
                  <td data-label="상태">
                    <StatusBadge
                      tone={
                        g.status === 'PENDING'
                          ? 'warning'
                          : g.status === 'APPROVED'
                            ? 'success'
                            : 'neutral'
                      }
                    >
                      {STATUS_LABEL[g.status]}
                    </StatusBadge>
                  </td>
                  <td data-label="등록 시각" className={styles.muted}>
                    {formatDateTime(g.createdAt)}
                  </td>
                  <td data-label="판단" className={styles.muted}>
                    {g.decidedBy ? `${g.decidedBy} · ${formatDateTime(g.decidedAt)}` : '-'}
                  </td>
                  {canDecide && (
                    <td data-label="작업" data-card-actions>
                      {g.status === 'PENDING' ? (
                        <div className={styles.rowActions}>
                          <Button
                            small
                            variant="gold"
                            onClick={() => {
                              // 창을 열 때 지난 오류를 지운다 — 뮤테이션이 부모에 있어
                              // 창만 닫으면 오류가 남는다.
                              approve.reset();
                              reject.reset();
                              setDecision({ group: g, kind: 'approve' });
                            }}
                          >
                            승인
                          </Button>
                          <Button
                            small
                            variant="danger"
                            onClick={() => {
                              approve.reset();
                              reject.reset();
                              setDecision({ group: g, kind: 'reject' });
                            }}
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
              {METAL_LABEL[decision.group.metalType]} 시세를 아래 값으로 즉시 적용한다.
              {decision.group.sell && (
                <>
                  <br />
                  내가 살 때 <strong>{formatPricePerGram(decision.group.sell.calcPrice)}</strong>
                </>
              )}
              {decision.group.buy && (
                <>
                  <br />
                  내가 팔 때 <strong>{formatPricePerGram(decision.group.buy.calcPrice)}</strong>
                </>
              )}
              <br />
              변동률 재검사는 하지 않는다 — 임계값 초과를 알고 승인하는 것이기 때문이다.
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

/**
 * 한 쪽(살 때 / 팔 때) 칸.
 *
 * <p>그 쪽이 대기에 걸리지 않았으면 비운다. "-" 하나로 두면 값이 0 인지 없는 것인지
 * 구분되지 않으므로 "해당 없음"이라고 적는다.
 */
function SideCell({ row }: { row: PricePending | null }) {
  if (!row) return <span className={styles.muted}>해당 없음</span>;
  return (
    <>
      <div className={styles.strong}>{formatPricePerGram(row.calcPrice)}</div>
      <div className={styles.muted}>
        직전 {formatPricePerGram(row.prevPrice)}
        {row.changePercent != null &&
          ` · ${row.changePercent > 0 ? '+' : ''}${row.changePercent}%`}
      </div>
    </>
  );
}
