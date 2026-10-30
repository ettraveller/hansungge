'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import { useAdminOrders, useAwaitingCount } from '@/hooks/admin/useOrders';
import { ApiError } from '@/types/api';
import type { OrderStatus } from '@/types/order';
import type { AdminOrderSummary } from '@/types/order-admin';
import { formatDateTime, formatKrw } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

/**
 * 주문 관리 (`/admin/orders`).
 *
 * <h2>발송 대기가 먼저 보인다</h2>
 * 관리자가 이 화면을 여는 이유는 대개 하나다 — 보낼 것이 있는가. 그래서 건수를 맨 위에
 * 두고, 탭의 첫 자리도 「발송 대기」다.
 *
 * <h2>발송 대기 판정은 서버가 한다</h2>
 * 「결제완료·준비중 + 송장번호 없음」이라는 조합을 화면이 다시 계산하지 않는다
 * ({@code awaitingShipment}). 둘이 갈라지면 줄 수와 상단 건수가 어긋난다.
 */
export function AdminOrdersView() {
  const [tab, setTab] = useState<TabKey>('AWAITING');
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  const search = {
    ...TABS[tab].search,
    keyword: query === '' ? null : query,
    page,
  };
  const { data, isLoading, error } = useAdminOrders(search);
  const awaiting = useAwaitingCount();

  const pick = (next: TabKey) => {
    setTab(next);
    setPage(0);
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>주문관리</h1>
          <p className={styles.subtitle}>
            결제가 끝난 주문을 확인하고 송장번호를 넣어 발송 처리한다.
          </p>
        </div>
        <div className={styles.headActions}>
          <span className={styles.hint}>
            발송 대기{' '}
            <strong style={{ color: 'var(--warn)' }}>{awaiting.data?.count ?? '—'}건</strong>
          </span>
        </div>
      </header>

      {error && (
        <Alert tone="danger" title="주문을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}

      <Panel
        title="주문 목록"
        note="송장번호가 비어 있는 결제완료 · 준비중 주문이 발송 대기다. 주문번호를 눌러 상세로 들어간다."
      >
        <div className={styles.form}>
          <div className={styles.statusTabs} role="group" aria-label="주문 상태 필터">
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
              placeholder="주문번호 또는 받는 분"
              aria-label="주문번호 또는 받는 분"
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
            <p className={styles.loading}>
              {tab === 'AWAITING' ? '발송할 주문이 없습니다.' : '해당하는 주문이 없습니다.'}
            </p>
          ) : (
            <>
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <caption className="sr-only">주문 목록</caption>
                  <thead>
                    <tr>
                      <th scope="col">주문번호</th>
                      <th scope="col">주문일시</th>
                      <th scope="col">받는 분</th>
                      <th scope="col">상품</th>
                      <th scope="col" className={styles.num}>
                        결제금액
                      </th>
                      <th scope="col">상태</th>
                      <th scope="col">송장번호</th>
                      <th scope="col">
                        <span className="sr-only">관리</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((row) => (
                      <OrderRow key={row.id} row={row} />
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

function OrderRow({ row }: { row: AdminOrderSummary }) {
  const track = row.shipment.trackingNo;
  return (
    <tr>
      <td data-label="주문번호" data-card-title>
        <Link href={`/admin/orders/${row.id}`}>{row.orderNo}</Link>
      </td>
      <td data-label="주문일시" className={styles.muted}>
        {formatDateTime(row.orderedAt)}
      </td>
      <td data-label="받는 분">{row.receiverName}</td>
      <td data-label="상품" className={styles.muted}>
        {row.orderName}
      </td>
      <td data-label="결제금액" className={`${styles.num} ${styles.strong}`}>
        {formatKrw(row.totalAmount)}
      </td>
      <td data-label="상태">
        <StatusBadge tone={toneOf(row)}>{row.statusLabel}</StatusBadge>
      </td>
      <td data-label="송장번호" className={track ? styles.num : styles.muted}>
        {track ?? '—'}
      </td>
      <td data-label="관리">
        <Link className={`${styles.btn} ${styles.btnSm}`} href={`/admin/orders/${row.id}`}>
          {row.awaitingShipment ? '송장 입력' : '상세'}
        </Link>
      </td>
    </tr>
  );
}

/**
 * 뱃지 색.
 *
 * <p>셋으로 줄인다. 관리자가 목록에서 한눈에 갈라야 하는 것은 "내가 지금 해야 하는가"
 * 하나뿐이고, 어느 단계인지는 뱃지 문구가 말한다 — {@code StatusBadge} 가 세운 원칙이다.
 * 상태마다 색을 주면 다섯 색이 되고 그게 전부 경고처럼 읽힌다.
 */
function toneOf(row: AdminOrderSummary): StatusTone {
  if (row.status === 'CANCELLED' || row.status === 'REFUNDED') return 'danger';
  if (row.awaitingShipment) return 'warning';
  return 'neutral';
}

type TabKey = 'AWAITING' | 'ALL' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

const TAB_ORDER: TabKey[] = ['AWAITING', 'ALL', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

const TABS: Record<
  TabKey,
  { label: string; search: { status?: OrderStatus | null; awaitingShipment?: boolean } }
> = {
  AWAITING: { label: '발송 대기', search: { awaitingShipment: true } },
  ALL: { label: '전체', search: {} },
  PAID: { label: '결제완료', search: { status: 'PAID' } },
  SHIPPED: { label: '배송중', search: { status: 'SHIPPED' } },
  DELIVERED: { label: '배송완료', search: { status: 'DELIVERED' } },
  CANCELLED: { label: '취소', search: { status: 'CANCELLED' } },
};
