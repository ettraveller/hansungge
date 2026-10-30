'use client';

import Link from 'next/link';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Panel } from '@/components/admin/Panel';
import { ShipmentPanel } from '@/components/admin/orders/ShipmentPanel';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useAdminOrder, useMarkPreparing } from '@/hooks/admin/useOrders';
import { ApiError } from '@/types/api';
import type { OrderStatus } from '@/types/order';
import { formatDateTime, formatGram, formatKrw } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

/**
 * 주문 상세 (`/admin/orders/{id}`).
 *
 * <h2>배송이 맨 위다</h2>
 * 이 화면에 들어오는 이유는 대개 "보내야 한다"이다. 주문 정보를 먼저 읽고 아래로
 * 스크롤해야 송장칸이 나오면, 하루에 여러 번 하는 일이 매번 한 동작씩 길어진다.
 *
 * <h2>금액을 다시 계산하지 않는다</h2>
 * 서버가 주문 시점 스냅샷을 준다. 화면이 더하거나 곱하면 이미 받은 돈과 다른 숫자가
 * 나올 수 있다 (원칙 P1).
 */
export function AdminOrderDetailView({ orderId }: { orderId: number }) {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');
  const { data: order, isLoading, error } = useAdminOrder(orderId);
  const prepare = useMarkPreparing();

  if (isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className={styles.page}>
        <Alert tone="danger" title="주문을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
        <div>
          <Link className={styles.btn} href="/admin/orders">
            목록으로
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <p className={styles.hint}>
            <Link href="/admin/orders">← 주문 목록</Link>
          </p>
          <h1 className={`${styles.title} ${styles.num}`}>{order.orderNo}</h1>
          <p className={styles.subtitle}>
            {formatDateTime(order.orderedAt)} 주문 · {order.receiverName}
          </p>
        </div>
        <div className={styles.headActions}>
          <StatusBadge tone={toneOf(order.status)}>{order.statusLabel}</StatusBadge>
        </div>
      </header>

      {prepare.error && (
        <Alert tone="danger">
          {prepare.error instanceof ApiError
            ? prepare.error.message
            : '상태를 바꾸지 못했습니다.'}
        </Alert>
      )}

      {/* 결제가 끝났는데 아직 준비 전이면, 준비 중으로 넘기는 길을 둔다.
          재고가 있으면 바로 발송해도 되므로 이 단계를 건너뛸 수 있다. */}
      {canEdit && order.status === 'PAID' && (
        <Panel
          title="진행"
          note="재고를 들고 있는 상품은 이 단계를 건너뛰고 바로 송장번호를 넣어도 된다."
        >
          <div className={styles.formActions}>
            <Button
              variant="primary"
              disabled={prepare.isPending}
              onClick={() => prepare.mutate(orderId)}
            >
              {prepare.isPending ? '처리 중…' : '상품 준비 중으로'}
            </Button>
          </div>
        </Panel>
      )}

      <ShipmentPanel orderId={orderId} canEdit={canEdit} />

      <Panel title="주문 품목" note="주문 시점에 확정된 금액이다. 시세가 바뀌어도 변하지 않는다.">
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <caption className="sr-only">주문 품목</caption>
            <thead>
              <tr>
                <th scope="col">상품</th>
                <th scope="col">옵션</th>
                <th scope="col" className={styles.num}>
                  중량
                </th>
                <th scope="col" className={styles.num}>
                  단가
                </th>
                <th scope="col" className={styles.num}>
                  수량
                </th>
                <th scope="col" className={styles.num}>
                  금액
                </th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item, index) => (
                <tr key={`${item.productId}-${index}`}>
                  <td data-label="상품" data-card-title className={styles.strong}>
                    {item.productName}
                  </td>
                  <td data-label="옵션" className={styles.muted}>
                    {item.optionName ?? '—'}
                  </td>
                  <td data-label="중량" className={styles.num}>
                    {item.weightG == null ? '—' : formatGram(item.weightG)}
                  </td>
                  <td data-label="단가" className={styles.num}>
                    {formatKrw(item.finalUnitPrice)}
                  </td>
                  <td data-label="수량" className={styles.num}>
                    {item.quantity}
                  </td>
                  <td data-label="금액" className={`${styles.num} ${styles.strong}`}>
                    {formatKrw(item.finalAmount)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <dl className={styles.detailList} style={{ marginTop: 16 }}>
          <div>
            <dt>상품 합계</dt>
            <dd className={styles.num}>{formatKrw(order.itemsAmount)}</dd>
          </div>
          <div>
            <dt>배송비</dt>
            <dd className={styles.num}>{formatKrw(order.shippingFee)}</dd>
          </div>
          {Number(order.discountAmount) > 0 && (
            <div>
              <dt>할인</dt>
              <dd className={styles.num}>- {formatKrw(order.discountAmount)}</dd>
            </div>
          )}
          <div>
            <dt>결제 금액</dt>
            <dd className={`${styles.num} ${styles.strong}`}>{formatKrw(order.totalAmount)}</dd>
          </div>
        </dl>
      </Panel>

      <Panel title="받는 분">
        <dl className={styles.detailList}>
          <div>
            <dt>이름</dt>
            <dd>{order.receiverName}</dd>
          </div>
          <div>
            <dt>연락처</dt>
            <dd className={styles.num}>{order.receiverPhone}</dd>
          </div>
          <div>
            <dt>주소</dt>
            <dd>
              ({order.zipcode}) {order.address1}
              {order.address2 ? ` ${order.address2}` : ''}
            </dd>
          </div>
          <div>
            <dt>요청사항</dt>
            <dd className={styles.muted}>{order.orderMemo || '—'}</dd>
          </div>
        </dl>
      </Panel>
    </div>
  );
}

/** 목록과 같은 기준으로 셋만 쓴다. 단계는 문구가 말한다. */
function toneOf(status: OrderStatus): StatusTone {
  if (status === 'CANCELLED' || status === 'REFUNDED' || status === 'REFUND_PENDING') {
    return 'danger';
  }
  if (status === 'PAID' || status === 'PREPARING') return 'warning';
  return 'neutral';
}
