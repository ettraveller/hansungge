'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { Toast } from '@/components/ui/Toast';
import { MyPageShell, myPageStyles as shell } from '@/layouts/MyPageShell';
import { useReorder } from '@/hooks/useCart';
import { useMyPageSummary } from '@/hooks/useInterest';
import { useOrders } from '@/hooks/useOrder';
import { ApiError } from '@/types/api';
import type { Order, OrderStatus } from '@/types/order';
import { formatDateTime, formatKrw } from '@/utils/format';
import styles from '@/views/OrderListView.module.css';

/**
 * 주문/배송내역.
 *
 * <p>금액은 <b>주문 시점 스냅샷</b>이라 다시 계산하지 않는다. 장바구니처럼 현재 시세로
 * 고쳐 보여주면, 지난달에 낸 돈이 오늘 시세로 바뀌어 보인다.
 */

/** 상태 묶음. 고객은 'PREPARING' 과 'PAID' 를 구분해 생각하지 않는다. */
const FILTERS: { key: string; name: string; match: (s: OrderStatus) => boolean }[] = [
  { key: 'all', name: '전체', match: () => true },
  {
    key: 'wait',
    name: '결제 대기',
    match: (s) => s === 'ORDER_CREATED' || s === 'PAYMENT_PENDING',
  },
  { key: 'prep', name: '준비 중', match: (s) => s === 'PAID' || s === 'PREPARING' },
  { key: 'ship', name: '배송', match: (s) => s === 'SHIPPED' || s === 'DELIVERED' },
  {
    key: 'cancel',
    name: '취소·환불',
    match: (s) =>
      s === 'CANCEL_REQUESTED' || s === 'CANCELLED' || s === 'REFUND_PENDING' || s === 'REFUNDED',
  },
];

export function OrderListView() {
  return (
    <MyPageShell title="주문/배송내역" lead="주문하신 시각의 시세로 확정된 금액입니다.">
      <OrderList />
    </MyPageShell>
  );
}

function OrderList() {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState('all');
  // 알림은 목록에 하나다. 주문마다 두면 연달아 담았을 때 겹쳐 쌓인다.
  const [toast, setToast] = useState<string | null>(null);
  const { data, isLoading, error } = useOrders(page, 20);
  const { data: summary } = useMyPageSummary();

  if (isLoading) {
    return <p className={shell.loading}>불러오는 중…</p>;
  }

  if (error) {
    return (
      <Notice tone="error" title="주문 내역을 불러오지 못했습니다">
        {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
      </Notice>
    );
  }

  const all = data?.content ?? [];
  const active = FILTERS.find((f) => f.key === filter) ?? FILTERS[0];
  const shown = all.filter((order) => active.match(order.status));

  // 탭의 숫자는 서버가 센 총계를 쓴다. 지금 화면의 한 장만 세면 "전체 6건"이
  // 다음 장으로 넘어갈 때 달라진다.
  const counts: Record<string, number | undefined> = {
    all: summary?.orderCount,
    wait: summary?.awaitingPayment,
    prep: summary?.preparing,
    ship: summary ? summary.shipping + summary.delivered : undefined,
    cancel: summary?.cancelled,
  };

  return (
    <>
      <div className={shell.tabs} role="tablist" aria-label="주문 상태">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            role="tab"
            aria-selected={f.key === filter}
            className={f.key === filter ? `${shell.tab} ${shell.tabOn}` : shell.tab}
            onClick={() => setFilter(f.key)}
          >
            {f.name}
            {counts[f.key] != null && <span className={shell.tabCount}>{counts[f.key]}</span>}
          </button>
        ))}
      </div>

      {shown.length === 0 ? (
        <div className={shell.empty}>
          <p>{all.length === 0 ? '아직 주문하신 내역이 없습니다.' : '이 상태의 주문이 없습니다.'}</p>
          {all.length === 0 && (
            <div className={shell.emptyAction}>
              <Link className={shell.pillDark} href="/shop">
                상품 보러 가기
              </Link>
            </div>
          )}
        </div>
      ) : (
        <div>
          {shown.map((order) => (
            <OrderRow key={order.orderNo} order={order} onReordered={setToast} />
          ))}
        </div>
      )}

      <Toast
        message={toast}
        onDone={() => setToast(null)}
        action={{ href: '/cart', label: '장바구니 보기' }}
      />

      {data && data.totalPages > 1 && (
        <div className={styles.pager}>
          <button
            type="button"
            className={shell.pill}
            disabled={data.first}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
          >
            이전
          </button>
          <span className={styles.pagerNow}>
            {data.number + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            className={shell.pill}
            disabled={data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </button>
        </div>
      )}
    </>
  );
}

function OrderRow({
  order,
  onReordered,
}: {
  order: Order;
  onReordered: (message: string) => void;
}) {
  const item = order.items[0];
  const reorder = useReorder();

  /**
   * 같은 상품을 다시 담는다.
   *
   * <p>수량과 규격까지 그대로 가져간다. 금액은 <b>가져가지 않는다</b> — 주문 금액은
   * 그때의 시세로 굳은 값이고, 장바구니는 오늘 시세로 다시 계산한다. 담은 뒤 값이
   * 달라져 있으면 장바구니가 차액을 알려 준다.
   */
  const addAllToCart = () => {
    const lines = order.items.map((i) => ({
      productId: i.productId,
      optionId: i.optionId,
      quantity: i.quantity,
    }));

    reorder.mutate(lines, {
      onSuccess: ({ added, failed }) => {
        if (added === 0) {
          onReordered('지금 담을 수 있는 상품이 없습니다');
        } else if (failed > 0) {
          onReordered(`${added}개를 담았습니다 · ${failed}개는 지금 담을 수 없습니다`);
        } else {
          onReordered(`${added}개를 장바구니에 담았습니다`);
        }
      },
      onError: () => onReordered('장바구니에 담지 못했습니다. 잠시 후 다시 시도해 주세요'),
    });
  };

  return (
    <article className={styles.row}>
      <div className={styles.rowHead}>
        <strong className={order.status === 'PAID' ? `${styles.state} ${styles.stateOk}` : styles.state}>
          {order.statusLabel}
        </strong>
        <Link href={`/mypage/orders/${order.orderNo}`} className={styles.orderNo}>
          {order.orderNo}
        </Link>
        <span className={styles.when}>{formatDateTime(order.orderedAt)}</span>
      </div>

      <div className={styles.body}>
        {/* 상품 사진 자리. 이미지 업로드(STEP 10)가 붙으면 여기에 들어온다 */}
        <div className={styles.thumb} aria-hidden="true">
          상품
          <br />
          사진
        </div>

        <div className={styles.info}>
          <div className={styles.name}>{order.orderName}</div>
          <div className={styles.price}>{formatKrw(order.totalAmount)}</div>
          {item && (
            <div className={styles.sub}>
              {item.quantity}개
              {item.marketPrice != null && ` · 주문 시각 시세 ${formatKrw(item.marketPrice)}/g`}
            </div>
          )}
        </div>
      </div>

      <div className={styles.acts}>
        {order.payable ? (
          <>
            <Link href={`/checkout/${order.orderNo}`} className={shell.pillDark}>
              결제하기
            </Link>
            <Link href={`/mypage/orders/${order.orderNo}`} className={shell.pill}>
              주문 상세
            </Link>
          </>
        ) : (
          <>
            {/* 결제 대기 주문에는 두지 않는다. 거기서 할 일은 다시 담는 것이 아니라 결제다. */}
            <button
              type="button"
              className={shell.pillDark}
              disabled={reorder.isPending}
              onClick={addAllToCart}
            >
              장바구니 담기
            </button>
            <Link href={`/mypage/orders/${order.orderNo}`} className={shell.pill}>
              주문 상세
            </Link>
            <Link href="/support/qna/write" className={shell.pill}>
              교환·반품 문의
            </Link>
          </>
        )}
      </div>
    </article>
  );
}
