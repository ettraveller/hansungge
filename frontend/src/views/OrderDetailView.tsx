'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { DepositGuide } from '@/components/order/DepositGuide';
import { ShipmentInfo } from '@/components/order/ShipmentInfo';
import { useCancelOrder, useOrder } from '@/hooks/useOrder';
import { ApiError } from '@/types/api';
import { PAYMENT_METHOD_LABEL, type OrderStatus } from '@/types/order';
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
  // 주문서에서 막 넘어왔는가 (무통장). 제목을 「주문이 접수되었습니다」로 바꾼다 —
  // 결제창을 거치지 않아 완료됐다는 느낌이 없으면 다시 주문하려 든다.
  const justPlaced = useSearchParams().get('placed') === '1';

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

  // 무통장 주문이 아직 입금 전인가. 입금이 확인되면(PAID) 안내가 사라지고
  // 그 자리를 배송칸이 이어받는다 — 둘이 같이 뜰 일은 없다.
  const awaitingDeposit =
    order.paymentMethod === 'BANK_TRANSFER' &&
    (order.status === 'ORDER_CREATED' || order.status === 'PAYMENT_PENDING');

  return (
    <div style={{ marginBottom: 60 }}>
      {/* 무통장 주문자가 이 화면에 오는 이유는 계좌번호 하나다. 맨 위에 둔다. */}
      {awaitingDeposit && <DepositGuide order={order} justPlaced={justPlaced} />}

      {/* 주문 상세 위에 둔다. 발송된 주문을 열어 보는 이유는 대개 "어디까지 왔나"
          하나라서, 상품 목록을 지나 내려가야 나오면 매번 한 동작씩 길어진다. */}
      <ShipmentInfo orderNo={order.orderNo} orderStatus={order.status} />

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
          <span>{amountLabel(order.status)}</span>
          <strong>{formatKrw(order.totalAmount)}</strong>
        </div>

        {/*
          과세 상품이 섞인 주문에만 적는다 (V38). 면세 주문에 「부가세 0원」을
          적으면 세금이 붙는 거래인데 면제받은 것처럼 읽히고, 금지금은 애초에
          과세 대상이 아니다.

          합계 <b>아래</b>에 둔다. 위에 두면 더해지는 값으로 읽히는데, 이 세액은
          결제 금액 안에 이미 들어 있다 - 판매가가 부가세 포함가다.
        */}
        {order.vatAmount > 0 && (
          <p className={styles.vatNote}>
            공급가액 {formatKrw(order.supplyAmount)} · 부가세 {formatKrw(order.vatAmount)}
            <span className={styles.vatHint}> (결제 금액에 포함)</span>
          </p>
        )}

        <dl className={styles.addressBox}>
          {/* 어떻게 치렀는지. 전에는 금액만 있고 수단이 없어, 무통장으로 넣은 주문인지
              카드로 낸 주문인지를 이 화면에서 알 수 없었다 (2026-11-23 QA-067). */}
          <dt>결제수단</dt>
          <dd>
            {PAYMENT_METHOD_LABEL[order.paymentMethod] ?? order.paymentMethod}
            {order.paymentMethod === 'BANK_TRANSFER' && order.depositorName
              ? ` · 입금자명 ${order.depositorName}`
              : ''}
          </dd>
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

        {/* 결제한 뒤의 취소·환불은 사람이 확인하고 처리한다 (환불은 돈이 나가는 일이다).
            문의 화면으로 보내되 주문번호를 실어 준다 — 전에는 빈 글쓰기 화면이 떠서
            주문번호를 손으로 옮겨 적어야 했다 (2026-11-23 QA-069). */}
        {!order.cancellable && AFTER_PAYMENT.includes(order.status) && (
          <>
            <p className={styles.disclaimer}>
              결제 후 취소·교환·반품은 아래에서 문의해 주세요. 발송 전이면 확인 후 바로
              취소해 드립니다.
            </p>
            <Link
              href={`/support/qna/write?orderNo=${encodeURIComponent(order.orderNo)}`}
              className={styles.ghostButton}
              style={{ display: 'block', textAlign: 'center' }}
            >
              취소·교환·반품 문의
            </Link>
          </>
        )}
      </article>
    </div>
  );
}

/** 결제가 끝난 뒤의 상태들. 이때부터는 손님이 직접 취소하지 못하고 문의로 받는다. */
const AFTER_PAYMENT: OrderStatus[] = ['PAID', 'PREPARING', 'SHIPPED', 'DELIVERED'];

/**
 * 합계의 이름.
 *
 * <p>전에는 PAID 일 때만 「결제 금액」이었다. 상품 준비 중·배송 중·배송 완료도 이미
 * 치른 주문인데 「결제 예정 금액」으로 나왔다 (2026-11-23 QA-067). 취소·환불된 주문은
 * 예정도 완료도 아니므로 「주문 금액」이라고만 한다.
 */
function amountLabel(status: OrderStatus): string {
  if (AFTER_PAYMENT.includes(status)) return '결제 금액';
  if (status === 'ORDER_CREATED' || status === 'PAYMENT_PENDING') return '결제 예정 금액';
  return '주문 금액';
}
