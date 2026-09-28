'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { readyPayment } from '@/api/order';
import { useOrder, usePaymentConfig } from '@/hooks/useOrder';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import { formatKrw } from '@/utils/format';
import styles from '@/views/CheckoutView.module.css';

/** 토스 SDK. 스크립트를 한 번만 넣고, 이미 있으면 그대로 쓴다. */
const TOSS_SDK = 'https://js.tosspayments.com/v2/standard';

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      widgets: (options: { customerKey: string }) => TossWidgets;
    };
  }
}

interface TossWidgets {
  setAmount(amount: { currency: string; value: number }): Promise<void>;
  renderPaymentMethods(options: { selector: string; variantKey?: string }): Promise<unknown>;
  renderAgreement(options: { selector: string }): Promise<unknown>;
  requestPayment(options: {
    orderId: string;
    orderName: string;
    customerName?: string;
    successUrl: string;
    failUrl: string;
  }): Promise<void>;
}

function loadTossSdk(): Promise<void> {
  if (window.TossPayments) return Promise.resolve();
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${TOSS_SDK}"]`);
  if (existing) {
    return new Promise((resolve, reject) => {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('토스 SDK 로드 실패')));
    });
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = TOSS_SDK;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('토스 SDK 로드 실패'));
    document.head.appendChild(script);
  });
}

/**
 * 결제 — 토스페이먼츠 결제위젯.
 *
 * <h2>금액은 화면이 정하지 않는다</h2>
 * 위젯에 넣는 금액은 서버가 확정한 <b>주문 금액</b>이다. 그리고 승인은 이 화면이 아니라
 * 서버가 한다 — 성공 주소로 돌아온 값은 브라우저를 거쳐 온 것이라 그대로 믿을 수 없다.
 *
 * <h2>결제창을 띄우기 전에 서버에 알린다</h2>
 * {@code /payments/{orderNo}/ready} 로 주문을 "결제 진행 중"으로 옮긴다. 이 표시가 있어야
 * 결제창까지 갔다가 돌아오지 않은 주문을 나중에 가려낼 수 있다.
 */
export function PaymentView({ orderNo }: { orderNo: string }) {
  const router = useRouter();
  const { user } = useUserSession();
  const { data: order, isLoading, error } = useOrder(orderNo);
  const { data: config } = usePaymentConfig();

  const widgetsRef = useRef<TossWidgets | null>(null);
  const [widgetReady, setWidgetReady] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [requesting, setRequesting] = useState(false);

  const amount = order?.totalAmount ?? 0;
  const canPay = !!order?.payable && !!config?.available && amount > 0;

  useEffect(() => {
    if (!canPay || !order || !config?.clientKey || widgetsRef.current) return;

    let cancelled = false;
    (async () => {
      try {
        await loadTossSdk();
        if (cancelled) return;
        // 스크립트는 받았는데 전역이 없다 — 조용히 넘어가면 빈 화면만 남는다.
        if (!window.TossPayments) throw new Error('결제 모듈을 초기화하지 못했습니다.');

        // 비회원 결제를 만들지 않으므로 고객 키는 항상 있다. 그래도 방어적으로 익명을 둔다.
        const customerKey = user ? `user-${user.id}` : 'ANONYMOUS';
        const widgets = window.TossPayments(config.clientKey).widgets({ customerKey });

        await widgets.setAmount({ currency: 'KRW', value: amount });
        await widgets.renderPaymentMethods({ selector: '#toss-payment-methods' });
        await widgets.renderAgreement({ selector: '#toss-agreement' });

        if (cancelled) return;
        widgetsRef.current = widgets;
        setWidgetReady(true);
      } catch (e) {
        if (!cancelled) {
          setFailure(e instanceof Error ? e.message : '결제창을 불러오지 못했습니다.');
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [canPay, order, config?.clientKey, amount, user]);

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

  // 이미 결제가 끝난 주문이면 결제창을 띄우지 않는다.
  if (!order.payable) {
    return (
      <div className={styles.empty}>
        <p>{order.statusLabel} 상태의 주문입니다.</p>
        <Link className={styles.emptyLink} href={`/mypage/orders/${order.orderNo}`}>
          주문 상세 보기
        </Link>
      </div>
    );
  }

  async function pay() {
    if (!widgetsRef.current || !order) return;
    setRequesting(true);
    setFailure(null);
    try {
      // 서버에 먼저 알린다. 실패하면 결제창을 띄우지 않는다 —
      // 기록 없이 결제되면 나중에 대조할 방법이 없다.
      await readyPayment(order.orderNo);

      const base = window.location.origin;
      await widgetsRef.current.requestPayment({
        orderId: order.orderNo,
        orderName: order.orderName,
        customerName: order.receiverName,
        successUrl: `${base}/checkout/success`,
        failUrl: `${base}/checkout/fail`,
      });
    } catch (e) {
      // 고객이 결제창을 닫은 것도 여기로 온다. 오류로 크게 표시하지 않는다.
      setRequesting(false);
      if (e instanceof ApiError) {
        setFailure(e.message);
      } else if (e instanceof Error && e.message) {
        setFailure(e.message);
      }
    }
  }

  return (
    <div className={styles.layout}>
      <div className={styles.form}>
        <h2 className={styles.sectionTitle}>결제 수단</h2>

        {config?.testMode && (
          <div className={styles.noticeWrap}>
            <Notice tone="warn" title="테스트 결제입니다">
              실제로 결제되지 않습니다. 카드 정보를 넣어도 금액이 빠져나가지 않습니다.
            </Notice>
          </div>
        )}

        {config && !config.available && (
          <div className={styles.noticeWrap}>
            <Notice tone="error" title="지금은 결제할 수 없습니다">
              결제 설정이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.
            </Notice>
          </div>
        )}

        {failure && (
          <div className={styles.noticeWrap}>
            <Notice tone="error" title="결제를 진행하지 못했습니다">
              {failure}
            </Notice>
          </div>
        )}

        <div id="toss-payment-methods" className={styles.widget} />
        <div id="toss-agreement" className={styles.agreement} />
      </div>

      <aside className={styles.summary} aria-label="결제 요약">
        <h2 className={styles.summaryTitle}>{order.orderName}</h2>

        <ul className={styles.rows}>
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
          <span>결제 금액</span>
          <strong>{formatKrw(order.totalAmount)}</strong>
        </div>

        <button
          type="button"
          className={styles.payButton}
          onClick={pay}
          disabled={!widgetReady || requesting}
        >
          {requesting ? '결제창을 여는 중…' : `${formatKrw(order.totalAmount)} 결제하기`}
        </button>

        <button
          type="button"
          className={styles.ghostButton}
          onClick={() => router.push(`/mypage/orders/${order.orderNo}`)}
        >
          나중에 결제하기
        </button>

        <p className={styles.disclaimer}>
          이 금액은 주문하신 시각의 시세로 <strong>이미 확정된</strong> 금액입니다. 결제를 마칠
          때까지 시세가 변해도 바뀌지 않습니다.
        </p>
      </aside>
    </div>
  );
}
