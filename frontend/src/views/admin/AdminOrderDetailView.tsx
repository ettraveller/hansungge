'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Panel } from '@/components/admin/Panel';
import { ShipmentPanel } from '@/components/admin/orders/ShipmentPanel';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useAdminOrder, useConfirmDeposit, useMarkPreparing } from '@/hooks/admin/useOrders';
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
  const confirmDeposit = useConfirmDeposit();

  // 무통장 주문이 아직 입금 전인가.
  const awaitingDeposit =
    order?.paymentMethod === 'BANK_TRANSFER' &&
    (order.status === 'ORDER_CREATED' || order.status === 'PAYMENT_PENDING');
  /**
   * 안내한 기한이 지났는가. 지나도 아무 일도 일어나지 않는다 — 사람이 보라고 적는다.
   *
   * <p>「지금」을 화면을 연 순간으로 한 번만 붙잡는다. 그릴 때마다 {@code Date.now()}
   * 를 부르면 같은 입력에 다른 결과가 나오고, 리렌더 시점에 따라 딱지가 깜빡인다.
   * 기한은 몇 시간 단위라 열어 둔 사이에 넘어가는 순간을 실시간으로 잡을 이유가 없다.
   */
  const [openedAt] = useState(() => Date.now());
  const depositOverdue =
    order?.depositDueAt != null && new Date(order.depositDueAt).getTime() < openedAt;

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

      {confirmDeposit.error && (
        <Alert tone="danger">
          {confirmDeposit.error instanceof ApiError
            ? confirmDeposit.error.message
            : '입금을 확인하지 못했습니다.'}
        </Alert>
      )}

      {/* 무통장 주문이 아직 입금 전이다.

          확인 버튼을 누르기 전에 <b>대조할 것</b>을 같은 화면에 모아 둔다 —
          입금자명과 금액. 통장을 보면서 이 둘을 맞춰 보는 것이 이 화면에서 하는
          일의 전부인데, 값이 아래 품목표에 흩어져 있으면 스크롤하며 외워야 한다. */}
      {canEdit && awaitingDeposit && (
        <Panel
          title="입금 확인"
          note="통장에 들어온 금액과 입금자명을 대조한 뒤 누른다. 누르면 결제 완료가 되고 고객 화면에서 입금 안내가 사라진다."
        >
          <dl className={styles.depositRows}>
            <div>
              <dt>입금자명</dt>
              <dd className={styles.strong}>{order.depositorName ?? '-'}</dd>
            </div>
            <div>
              <dt>입금 금액</dt>
              <dd className={`${styles.strong} ${styles.num}`}>{formatKrw(order.totalAmount)}</dd>
            </div>
            <div>
              <dt>안내한 기한</dt>
              <dd>
                {formatDateTime(order.depositDueAt)}
                {/* 기한이 지나면 스위퍼가 접는다 (2026-11-05). 여기 「기한 지남」이
                    보이는데 주문이 아직 살아 있다면, 스위퍼가 다음 바퀴를 돌기 전이다
                    (1분 주기). 접히기 전에 입금이 확인되면 그대로 눌러도 된다. */}
                {depositOverdue && <span className={styles.overdue}>기한 지남</span>}
              </dd>
            </div>
          </dl>

          <div className={styles.formActions}>
            <Button
              variant="primary"
              disabled={confirmDeposit.isPending}
              onClick={() => confirmDeposit.mutate(orderId)}
            >
              {confirmDeposit.isPending ? '처리 중…' : '입금 확인'}
            </Button>
          </div>
        </Panel>
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
