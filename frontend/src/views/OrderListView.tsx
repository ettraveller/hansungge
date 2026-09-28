'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { useOrders } from '@/hooks/useOrder';
import { ApiError } from '@/types/api';
import type { Order, OrderStatus } from '@/types/order';
import { formatDateTime, formatKrw } from '@/utils/format';
import styles from '@/views/CheckoutView.module.css';

/** 상태에 따라 배지 색을 바꾼다. 색만으로 알리지 않으므로 문구는 항상 함께 나온다. */
export function statusClass(status: OrderStatus): string {
  if (status === 'PAID' || status === 'PREPARING' || status === 'SHIPPED' || status === 'DELIVERED') {
    return `${styles.statusBadge} ${styles.statusPaid}`;
  }
  if (status === 'CANCELLED' || status === 'REFUNDED' || status === 'REFUND_PENDING') {
    return `${styles.statusBadge} ${styles.statusCancelled}`;
  }
  return styles.statusBadge;
}

/**
 * 주문 내역.
 *
 * <p>금액은 <b>주문 시점 스냅샷</b>이라 다시 계산하지 않는다. 장바구니처럼 현재 시세로
 * 고쳐 보여주면, 지난달에 낸 돈이 오늘 시세로 바뀌어 보인다.
 */
export function OrderListView() {
  const [page, setPage] = useState(0);
  const { data, isLoading, error } = useOrders(page);

  if (isLoading) {
    return <p className={styles.loading}>불러오는 중…</p>;
  }

  if (error) {
    return (
      <div className={styles.noticeWrap}>
        <Notice tone="error" title="주문 내역을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Notice>
      </div>
    );
  }

  if (!data || data.content.length === 0) {
    return (
      <div className={styles.empty}>
        <p>아직 주문하신 내역이 없습니다.</p>
        <Link className={styles.emptyLink} href="/shop">
          상품 보러 가기
        </Link>
      </div>
    );
  }

  return (
    <div style={{ marginBottom: 60 }}>
      <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: 14 }}>
        {data.content.map((order) => (
          <li key={order.orderNo}>
            <OrderCard order={order} />
          </li>
        ))}
      </ul>

      {data.totalPages > 1 && (
        <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 24 }}>
          <button
            type="button"
            className={styles.ghostButton}
            style={{ width: 'auto', padding: '9px 18px' }}
            disabled={data.first}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
          >
            이전
          </button>
          <span style={{ alignSelf: 'center', fontSize: 13, color: 'var(--gray)' }}>
            {data.number + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            className={styles.ghostButton}
            style={{ width: 'auto', padding: '9px 18px' }}
            disabled={data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}

function OrderCard({ order }: { order: Order }) {
  return (
    <article className={styles.form}>
      <div className={styles.orderHead}>
        <Link href={`/mypage/orders/${order.orderNo}`} className={styles.orderNo}>
          {order.orderNo}
        </Link>
        <span className={statusClass(order.status)}>{order.statusLabel}</span>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--gray)' }}>
          {formatDateTime(order.orderedAt)}
        </span>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ fontSize: 14, color: 'var(--dark)' }}>{order.orderName}</div>
        <strong style={{ fontSize: 16, fontVariantNumeric: 'tabular-nums' }}>
          {formatKrw(order.totalAmount)}
        </strong>
      </div>

      {order.payable && (
        <Link
          href={`/checkout/${order.orderNo}`}
          className={styles.emptyLink}
          style={{ marginTop: 14 }}
        >
          결제하기
        </Link>
      )}
    </article>
  );
}
