'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import { useAdminPayments } from '@/hooks/admin/usePayments';
import type { AdminPayment, PaymentStatus } from '@/api/admin/payments';
import { ApiError } from '@/types/api';
import { formatDateTime, formatKrw } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

/**
 * 결제 관리 (`/admin/payments`) — 조회 전용.
 *
 * <h2>주문관리와 무엇이 다른가</h2>
 * 주문은 "물건을 보내야 하는가"의 화면이고, 여기는 <b>"돈이 실제로 오갔는가"</b>다.
 * 줄의 단위도 주문이 아니라 결제 시도라, 한 주문이 여러 줄일 수 있다 — 결제창에서
 * 나갔다가 다시 시도한 흔적까지 그대로 보인다. PG 대시보드와 대조하는 자리다.
 *
 * <h2>쓰기가 없다</h2>
 * 승인·취소는 PG 와의 흐름(결제창·웹훅·향후 취소 API)에서만 일어난다. 여기서 행을
 * 고치게 두면 기록과 사실이 갈라진다.
 */
export function AdminPaymentsView() {
  const [tab, setTab] = useState<TabKey>('ALL');
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useAdminPayments({
    status: TABS[tab].status,
    keyword: query === '' ? null : query,
    page,
  });

  const pick = (next: TabKey) => {
    setTab(next);
    setPage(0);
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>결제관리</h1>
          <p className={styles.subtitle}>
            돈이 실제로 오간 기록이다. 주문 한 건에 결제 시도가 여러 줄일 수 있다.
          </p>
        </div>
      </header>

      {error && (
        <Alert tone="danger" title="결제 내역을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}

      <Panel
        title="결제 내역"
        note="승인·취소는 여기서 하지 않는다 — 결제 흐름(결제창·웹훅)과 토스 대시보드가 한다. 주문번호를 눌러 주문 상세로 간다."
      >
        <div className={styles.form}>
          <div className={styles.statusTabs} role="group" aria-label="결제 상태 필터">
            {TAB_ORDER.map((key) => (
              <button
                key={key}
                type="button"
                className={key === tab ? styles.statusTabOn : styles.statusTab}
                aria-pressed={key === tab}
                onClick={() => pick(key)}
              >
                {TABS[key].label}
              </button>
            ))}
          </div>

          <form
            className={styles.filters}
            onSubmit={(e) => {
              e.preventDefault();
              setQuery(keyword.trim());
              setPage(0);
            }}
          >
            <input
              className={styles.input}
              style={{ maxWidth: 280 }}
              type="search"
              value={keyword}
              placeholder="주문번호"
              aria-label="주문번호"
              onChange={(e) => setKeyword(e.target.value)}
            />
            <button type="submit" className={`${styles.btn} ${styles.btnPrimary}`}>
              검색
            </button>
            {query !== '' && (
              <button
                type="button"
                className={styles.btn}
                onClick={() => {
                  setKeyword('');
                  setQuery('');
                  setPage(0);
                }}
              >
                초기화
              </button>
            )}
          </form>

          {isLoading ? (
            <p className={styles.loading}>불러오는 중…</p>
          ) : !data || data.content.length === 0 ? (
            <p className={styles.loading}>해당하는 결제 내역이 없습니다.</p>
          ) : (
            <>
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <caption className="sr-only">결제 내역</caption>
                  <thead>
                    <tr>
                      <th scope="col">주문번호</th>
                      <th scope="col">시도일시</th>
                      <th scope="col">결제사</th>
                      <th scope="col">수단</th>
                      <th scope="col" className={styles.num}>
                        금액
                      </th>
                      <th scope="col">상태</th>
                      <th scope="col">승인일시</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((row) => (
                      <PaymentRow key={row.id} row={row} />
                    ))}
                  </tbody>
                </table>
              </div>

              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onChange={(next) => setPage(next)}
              />
            </>
          )}
        </div>
      </Panel>
    </div>
  );
}

function PaymentRow({ row }: { row: AdminPayment }) {
  return (
    <tr>
      <td data-label="주문번호" data-card-title>
        <Link href={`/admin/orders/${row.orderId}`}>{row.orderNo}</Link>
      </td>
      <td data-label="시도일시" className={styles.muted}>
        {formatDateTime(row.createdAt)}
      </td>
      <td data-label="결제사">{PROVIDER_LABEL[row.provider] ?? row.provider}</td>
      <td data-label="수단">{methodLabel(row.method)}</td>
      <td data-label="금액" className={`${styles.num} ${styles.strong}`}>
        {formatKrw(row.amount)}
      </td>
      <td data-label="상태">
        <StatusBadge tone={toneOf(row.status)}>{row.statusLabel}</StatusBadge>
      </td>
      <td data-label="승인일시" className={styles.muted}>
        {row.approvedAt ? formatDateTime(row.approvedAt) : '—'}
      </td>
    </tr>
  );
}

const PROVIDER_LABEL: Record<string, string> = {
  TOSS: '토스페이먼츠',
  BANK_TRANSFER: '무통장',
};

/** 수단은 PG 승인 응답의 한글 값(카드·간편결제…)이 그대로 온다. 무통장만 우리 값이다. */
function methodLabel(method: string | null): string {
  if (!method) return '—';
  return method === 'BANK_TRANSFER' ? '무통장입금' : method;
}

/**
 * 뱃지 색. 주문 목록과 같은 원칙 — 한눈에 갈라야 하는 것만 색으로 말한다.
 * 승인은 초록(돈이 들어옴), 실패·취소는 빨강, 시도 중은 중립이다.
 */
function toneOf(status: PaymentStatus): StatusTone {
  if (status === 'APPROVED') return 'success';
  if (status === 'FAILED' || status === 'CANCELLED') return 'danger';
  return 'neutral';
}

type TabKey = 'ALL' | 'APPROVED' | 'READY' | 'FAILED' | 'CANCELLED';

const TAB_ORDER: TabKey[] = ['ALL', 'APPROVED', 'READY', 'FAILED', 'CANCELLED'];

const TABS: Record<TabKey, { label: string; status: PaymentStatus | null }> = {
  ALL: { label: '전체', status: null },
  APPROVED: { label: '승인', status: 'APPROVED' },
  READY: { label: '시도', status: 'READY' },
  FAILED: { label: '실패', status: 'FAILED' },
  CANCELLED: { label: '취소', status: 'CANCELLED' },
};
