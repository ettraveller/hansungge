'use client';

import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { useCart } from '@/hooks/useCart';
import { useCreateOrder } from '@/hooks/useOrder';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import { formatKrw, formatPhone } from '@/utils/format';
import styles from '@/views/CheckoutView.module.css';

/**
 * 주문서 작성 — 배송지를 받고 주문을 만든다.
 *
 * <h2>금액을 보내지 않는다</h2>
 * 화면이 보여주는 합계는 <b>안내용</b>이다. 실제 금액은 주문을 만드는 순간 서버가
 * 장바구니를 다시 읽고 그 시점 시세로 계산한다 (원칙 P1). 그래서 화면 합계와 결제 금액이
 * 다를 수 있고, 다음 화면에서 확정 금액을 다시 보여준다.
 *
 * <h2>여기서 결제하지 않는다</h2>
 * 주문서를 먼저 만들고 결제는 다음 단계다. 한 화면에서 둘을 같이 하면, 결제창에서
 * 취소한 고객의 주문이 아예 남지 않아 "무엇을 사려 했는지"가 사라진다.
 */
export function CheckoutView() {
  const router = useRouter();
  const { user } = useUserSession();
  const { data: cart, isLoading } = useCart();
  const createOrder = useCreateOrder();

  const [receiverName, setReceiverName] = useState('');
  const [receiverPhone, setReceiverPhone] = useState('');
  const [zipcode, setZipcode] = useState('');
  const [address1, setAddress1] = useState('');
  const [address2, setAddress2] = useState('');
  const [orderMemo, setOrderMemo] = useState('');

  // 회원 정보를 기본값으로 넣어 둔다. 대부분 자기 주소로 받는다.
  const [prefilled, setPrefilled] = useState(false);
  if (!prefilled && user) {
    setPrefilled(true);
    setReceiverName(user.name ?? '');
    setReceiverPhone(formatPhone(user.phone) || '');
  }

  const orderableItems = cart?.items.filter((item) => item.orderable) ?? [];
  const canSubmit =
    orderableItems.length > 0 &&
    receiverName.trim() !== '' &&
    receiverPhone.trim() !== '' &&
    zipcode.trim() !== '' &&
    address1.trim() !== '' &&
    !createOrder.isPending;

  if (isLoading) {
    return <p className={styles.loading}>불러오는 중…</p>;
  }

  if (orderableItems.length === 0) {
    return (
      <div className={styles.empty}>
        <p>주문할 수 있는 상품이 없습니다.</p>
        <Link className={styles.emptyLink} href="/cart">
          장바구니로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className={styles.layout}>
      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          if (!canSubmit) return;
          createOrder.mutate(
            {
              receiverName: receiverName.trim(),
              receiverPhone: receiverPhone.trim(),
              zipcode: zipcode.trim(),
              address1: address1.trim(),
              address2: address2.trim() || undefined,
              orderMemo: orderMemo.trim() || undefined,
            },
            { onSuccess: (order) => router.push(`/checkout/${order.orderNo}`) },
          );
        }}
      >
        <h2 className={styles.sectionTitle}>받는 분</h2>

        {createOrder.error && (
          <div className={styles.noticeWrap}>
            <Notice tone="error" title="주문서를 만들지 못했습니다">
              {createOrder.error instanceof ApiError
                ? createOrder.error.message
                : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        <div className={styles.field}>
          <label className={styles.label} htmlFor="co-name">
            이름
          </label>
          <input
            id="co-name"
            className={styles.input}
            value={receiverName}
            onChange={(e) => setReceiverName(e.target.value)}
            maxLength={100}
            autoComplete="name"
            required
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="co-phone">
            연락처
          </label>
          <input
            id="co-phone"
            className={styles.input}
            value={receiverPhone}
            onChange={(e) => setReceiverPhone(e.target.value)}
            placeholder="010-1234-5678"
            maxLength={30}
            autoComplete="tel"
            required
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="co-zip">
            우편번호
          </label>
          <input
            id="co-zip"
            className={styles.inputShort}
            value={zipcode}
            onChange={(e) => setZipcode(e.target.value)}
            maxLength={10}
            inputMode="numeric"
            autoComplete="postal-code"
            required
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="co-addr1">
            주소
          </label>
          <input
            id="co-addr1"
            className={styles.input}
            value={address1}
            onChange={(e) => setAddress1(e.target.value)}
            maxLength={200}
            autoComplete="address-line1"
            required
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="co-addr2">
            상세 주소 <span className={styles.optional}>(선택)</span>
          </label>
          <input
            id="co-addr2"
            className={styles.input}
            value={address2}
            onChange={(e) => setAddress2(e.target.value)}
            maxLength={200}
            autoComplete="address-line2"
          />
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="co-memo">
            배송 요청사항 <span className={styles.optional}>(선택)</span>
          </label>
          <input
            id="co-memo"
            className={styles.input}
            value={orderMemo}
            onChange={(e) => setOrderMemo(e.target.value)}
            maxLength={300}
            placeholder="부재 시 경비실에 맡겨 주세요"
          />
        </div>

        <button type="submit" className={styles.submit} disabled={!canSubmit}>
          {createOrder.isPending ? '주문서를 만드는 중…' : '주문서 작성'}
        </button>
      </form>

      <aside className={styles.summary} aria-label="주문 요약">
        <h2 className={styles.summaryTitle}>주문 상품</h2>

        <ul className={styles.items}>
          {orderableItems.map((item) => (
            <li key={item.id} className={styles.item}>
              <div className={styles.itemName}>
                {item.productName}
                {item.optionName && <span className={styles.itemOption}> · {item.optionName}</span>}
              </div>
              <div className={styles.itemMeta}>
                <span>{item.quantity}개</span>
                <strong>{formatKrw(item.lineAmount)}</strong>
              </div>
            </li>
          ))}
        </ul>

        <div className={styles.total}>
          <span>예상 금액</span>
          <strong>{formatKrw(cart?.subtotal)}</strong>
        </div>

        <p className={styles.disclaimer}>
          <strong>최종 금액은 주문서를 만드는 시각의 시세로 확정됩니다.</strong> 금이 시세 상품이라
          지금 보이는 금액과 다를 수 있고, 다음 화면에서 확정 금액을 다시 확인하신 뒤 결제하시게
          됩니다.
        </p>
      </aside>
    </div>
  );
}
