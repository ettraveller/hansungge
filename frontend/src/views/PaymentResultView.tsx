'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { failPayment } from '@/api/order';
import { useConfirmPayment } from '@/hooks/useOrder';
import { ApiError } from '@/types/api';
import type { Order } from '@/types/order';
import { formatKrw } from '@/utils/format';
import styles from '@/views/CheckoutView.module.css';

/**
 * 결제 성공 — 토스가 돌려보낸 자리.
 *
 * <h2>여기서 결제가 끝난 것이 아니다</h2>
 * 토스는 {@code paymentKey}·{@code orderId}·{@code amount} 를 주소에 붙여 보낸다. 하지만
 * <b>승인은 서버가 해야</b> 성립한다. 이 화면은 서버에 승인을 부탁할 뿐이고, 주소의 금액은
 * 서버로 보내지 않는다 — 주소창에서 고칠 수 있는 값이기 때문이다.
 *
 * <h2>한 번만 부른다</h2>
 * 새로고침하면 같은 승인이 다시 나간다. 서버가 멱등키로 막지만, 화면에서도 한 번만 부른다.
 */
export function PaymentSuccessView() {
  const params = useSearchParams();
  const orderNo = params.get('orderId');
  const paymentKey = params.get('paymentKey');

  const confirm = useConfirmPayment();
  const [order, setOrder] = useState<Order | null>(null);
  const called = useRef(false);

  useEffect(() => {
    if (called.current || !orderNo || !paymentKey) return;
    called.current = true;
    confirm.mutate({ orderNo, paymentKey }, { onSuccess: setOrder });
  }, [orderNo, paymentKey, confirm]);

  if (!orderNo || !paymentKey) {
    return (
      <div className={styles.empty}>
        <p>결제 정보가 올바르지 않습니다.</p>
        <Link className={styles.emptyLink} href="/mypage/orders">
          주문 내역으로
        </Link>
      </div>
    );
  }

  if (confirm.isPending || (!order && !confirm.error)) {
    return (
      <p className={styles.loading}>
        결제를 확인하고 있습니다. <strong>창을 닫지 마세요.</strong>
      </p>
    );
  }

  if (confirm.error) {
    const message =
      confirm.error instanceof ApiError
        ? confirm.error.message
        : '결제 확인에 실패했습니다. 고객센터로 문의해 주세요.';
    return (
      <div className={styles.form}>
        <Notice tone="error" title="결제를 완료하지 못했습니다">
          {message}
          <br />
          <strong>
            카드에서 금액이 빠져나갔다면 자동으로 취소됩니다. 확인이 필요하시면 주문번호{' '}
            {orderNo} 로 문의해 주세요.
          </strong>
        </Notice>
        <Link className={styles.emptyLink} href={`/mypage/orders/${orderNo}`}>
          주문 상세 보기
        </Link>
      </div>
    );
  }

  return (
    <div className={styles.form}>
      <div className={styles.orderHead}>
        <span className={styles.orderNo}>{order?.orderNo}</span>
        <span className={`${styles.statusBadge} ${styles.statusPaid}`}>{order?.statusLabel}</span>
      </div>

      <Notice tone="info" title="결제가 완료되었습니다">
        {order?.orderName} · {formatKrw(order?.totalAmount)}
        <br />
        준비되는 대로 배송해 드리겠습니다.
      </Notice>

      <div style={{ display: 'flex', gap: 8, marginTop: 20 }}>
        <Link className={styles.emptyLink} href={`/mypage/orders/${order?.orderNo}`}>
          주문 상세
        </Link>
        <Link className={styles.emptyLink} href="/shop">
          쇼핑 계속하기
        </Link>
      </div>
    </div>
  );
}

/**
 * 결제 실패 — 고객이 창을 닫았거나 카드가 거절됐다.
 *
 * <p><b>주문은 지우지 않는다.</b> 다른 수단으로 다시 낼 수 있어야 하고, 무엇을 사려 했는지가
 * 남아 있어야 고객센터가 도울 수 있다.
 */
export function PaymentFailView() {
  const params = useSearchParams();
  const orderNo = params.get('orderId');
  const message = params.get('message');
  const code = params.get('code');
  const reported = useRef(false);

  useEffect(() => {
    if (reported.current || !orderNo) return;
    reported.current = true;
    // 기록이 실패해도 화면은 그대로 둔다 — 고객에게 보여줄 것은 결제가 안 됐다는 사실뿐이다.
    failPayment(orderNo, code ?? message ?? undefined).catch(() => {});
  }, [orderNo, code, message]);

  return (
    <div className={styles.form}>
      <Notice tone="warn" title="결제가 완료되지 않았습니다">
        {message || '결제가 취소되었거나 승인되지 않았습니다.'}
        <br />
        주문은 그대로 남아 있습니다. 다른 결제 수단으로 다시 시도하실 수 있습니다.
      </Notice>

      <div style={{ display: 'flex', gap: 8, marginTop: 20 }}>
        {orderNo && (
          <Link className={styles.emptyLink} href={`/checkout/${orderNo}`}>
            다시 결제하기
          </Link>
        )}
        <Link className={styles.emptyLink} href="/mypage/orders">
          주문 내역으로
        </Link>
      </div>
    </div>
  );
}
