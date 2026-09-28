'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { useCancelOrder, useOrder } from '@/hooks/useOrder';
import { ApiError } from '@/types/api';
import type { OrderStatus } from '@/types/order';
import { formatDateTime, formatKrw, formatPhone } from '@/utils/format';
import styles from '@/views/CheckoutView.module.css';

/**
 * 주문 상세 — 주문 시점 스냅샷을 그대로 보여준다.
 *
 * <p>항목마다 <b>그때의 시세</b>가 함께 나온다. "왜 이 금액이었나"를 고객이 스스로 확인할 수
 * 있어야 시세 상품에 대한 문의가 줄어든다.
 */
/**
 * 상태에 따라 배지 색을 바꾼다. <b>색만으로 알리지 않으므로</b> 문구는 항상 함께 나온다.
 */
function badgeClass(status: OrderStatus): string {
  if (status === 'PAID' || status === 'PREPARING' || status === 'SHIPPED' || status === 'DELIVERED') {
    return `${styles.statusBadge} ${styles.statusPaid}`;
  }
  if (status === 'CANCELLED' || status === 'REFUNDED' || status === 'REFUND_PENDING') {
    return `${styles.statusBadge} ${styles.statusCancelled}`;
  }
  return styles.statusBadge;
}

export function OrderDetailView({ orderNo }: { orderNo: string }) {
  const { data: order, isLoading, error } = useOrder(orderNo);
  const cancel = useCancelOrder();
  const [confirming, setConfirming] = useState(false);

  if (isLoading) {
    return <p className={styles.loading}>불러오는 중…</p>;
  }

  if (error || !order) {
    return (
      <div className={styles.empty}>
        <p>{error instanceof ApiError ? error.message : '주문을 찾을 수 없습니다.'}</p>
        <Link className={styles.emptyLink} href="/mypage/orders">
          주문 내역으로
        </Link>
      </div>
    );
  }

  return (
    <div style={{ marginBottom: 60 }}>
      <article className={styles.form}>
        <div className={styles.orderHead}>
          <span className={styles.orderNo}>{order.orderNo}</span>
          <span className={badgeClass(order.status)}>{order.statusLabel}</span>
          <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--gray)' }}>
            {formatDateTime(order.orderedAt)}
          </span>
        </div>

        {cancel.error && (
          <div className={styles.noticeWrap}>
            <Notice tone="error" title="취소하지 못했습니다">
              {cancel.error instanceof ApiError
                ? cancel.error.message
                : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        <h2 className={styles.sectionTitle}>주문 상품</h2>
        <ul className={styles.items}>
          {order.items.map((item, index) => (
            <li key={`${item.productId}-${index}`} className={styles.item}>
              <div className={styles.itemName}>
                {item.productName}
                {item.optionName && <span className={styles.itemOption}> · {item.optionName}</span>}
              </div>
              <div className={styles.itemMeta}>
                <span>
                  {item.quantity}개 · {formatKrw(item.finalUnitPrice)}
                  {/* 그때의 시세를 함께 남긴다 — 금액의 근거다 */}
                  {item.marketPrice != null && ` (주문 시각 시세 ${formatKrw(item.marketPrice)}/g)`}
                </span>
                <strong>{formatKrw(item.finalAmount)}</strong>
              </div>
            </li>
          ))}
        </ul>

        <ul className={styles.rows} style={{ marginTop: 16 }}>
          <li>
            <span>상품 금액</span>
            <strong>{formatKrw(order.itemsAmount)}</strong>
          </li>
          <li>
            <span>배송비</span>
            <strong>{order.shippingFee > 0 ? formatKrw(order.shippingFee) : '무료'}</strong>
          </li>
          {order.discountAmount > 0 && (
            <li>
              <span>할인</span>
              <strong>-{formatKrw(order.discountAmount)}</strong>
            </li>
          )}
        </ul>

        <div className={styles.total}>
          <span>{order.status === 'PAID' ? '결제 금액' : '결제 예정 금액'}</span>
          <strong>{formatKrw(order.totalAmount)}</strong>
        </div>

        <dl className={styles.addressBox}>
          <dt>받는 분</dt>
          <dd>
            {order.receiverName} · {formatPhone(order.receiverPhone)}
          </dd>
          <dt>주소</dt>
          <dd>
            ({order.zipcode}) {order.address1} {order.address2}
          </dd>
          {order.orderMemo && (
            <>
              <dt>요청사항</dt>
              <dd>{order.orderMemo}</dd>
            </>
          )}
        </dl>

        {order.payable && (
          <Link href={`/checkout/${order.orderNo}`} className={styles.emptyLink} style={{ marginTop: 18 }}>
            결제하기
          </Link>
        )}

        {order.cancellable &&
          (confirming ? (
            <div style={{ marginTop: 14 }}>
              <Notice tone="warn" title="주문을 취소할까요?">
                취소하면 되돌릴 수 없습니다. 상품은 다시 담아 주문하셔야 합니다.
              </Notice>
              <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                <button
                  type="button"
                  className={styles.ghostButton}
                  style={{ width: 'auto', padding: '9px 18px' }}
                  disabled={cancel.isPending}
                  onClick={() => cancel.mutate(order.orderNo, { onSuccess: () => setConfirming(false) })}
                >
                  {cancel.isPending ? '취소하는 중…' : '주문 취소'}
                </button>
                <button
                  type="button"
                  className={styles.ghostButton}
                  style={{ width: 'auto', padding: '9px 18px' }}
                  onClick={() => setConfirming(false)}
                >
                  그대로 두기
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              className={styles.ghostButton}
              onClick={() => setConfirming(true)}
            >
              주문 취소
            </button>
          ))}

        {!order.cancellable && order.status === 'PAID' && (
          <p className={styles.disclaimer}>
            결제 후 취소·환불은 고객센터(1551-0000)로 문의해 주세요.
          </p>
        )}
      </article>
    </div>
  );
}
