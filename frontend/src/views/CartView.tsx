'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

import { Notice } from '@/components/auth/Notice';
import {
  useCart,
  useChangeCartQuantity,
  useClearCart,
  useRemoveCartItem,
} from '@/hooks/useCart';
import { useUserSession } from '@/hooks/useUserSession';
import { formatGram, formatKrw } from '@/utils/format';
import { METAL_LABEL } from '@/types/price';
import { ApiError } from '@/types/api';
import type { CartItem } from '@/types/cart';
import styles from './CartView.module.css';

/**
 * 장바구니 (개발계획 §13.6).
 *
 * <p>이 화면은 <b>금액을 하나도 계산하지 않는다.</b> 단가·줄 합계·차액·총합이 전부 서버가
 * 내려준 값이다. 수량을 바꾸면 서버가 다시 계산한 장바구니 전체를 돌려주고, 화면은 그것을
 * 그대로 그린다 (원칙 P1).
 *
 * <p>담은 뒤 시세가 바뀌었거나 재고가 빠진 줄은 서버가 표시해 준다. 그런 줄은 합계에서
 * 빠져 있으므로, 화면 합계와 실제 결제 금액이 어긋나지 않는다.
 */
export function CartView() {
  const router = useRouter();
  const { restored, isAuthenticated } = useUserSession();
  const { data: cart, isLoading, error } = useCart();

  useEffect(() => {
    if (restored && !isAuthenticated) {
      router.replace('/login?reason=required&next=%2Fcart');
    }
  }, [restored, isAuthenticated, router]);

  if (!restored) return <p className={styles.loading}>확인 중…</p>;
  if (!isAuthenticated) return <p className={styles.loading}>로그인 화면으로 이동합니다…</p>;

  return (
    <div className="wrap">
      <header className={styles.head}>
        <h1 className={styles.title}>장바구니</h1>
        <p className={styles.lead}>
          금액은 지금 시세로 다시 계산한 값입니다. 주문 시점에 한 번 더 확인됩니다.
        </p>
      </header>

      {error && (
        <Notice tone="error" title="장바구니를 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Notice>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : !cart || cart.itemCount === 0 ? (
        <EmptyCart />
      ) : (
        <>
          {cart.notice && (
            <div className={styles.noticeWrap}>
              <Notice tone={cart.unavailableCount > 0 ? 'warn' : 'info'}>{cart.notice}</Notice>
            </div>
          )}

          <div className={styles.layout}>
            <section className={styles.list} aria-label="담긴 상품">
              {cart.items.map((item) => (
                <CartRow key={item.id} item={item} />
              ))}
              <ClearButton />
            </section>

            <Summary
              subtotal={cart.subtotal}
              orderable={cart.orderable}
              unavailableCount={cart.unavailableCount}
              totalQuantity={cart.totalQuantity}
            />
          </div>
        </>
      )}
    </div>
  );
}

function EmptyCart() {
  return (
    <div className={styles.empty}>
      <p className={styles.emptyText}>장바구니가 비어 있습니다.</p>
      <Link className={styles.emptyLink} href="/shop">
        상품 보러 가기
      </Link>
    </div>
  );
}

function CartRow({ item }: { item: CartItem }) {
  const changeQuantity = useChangeCartQuantity();
  const remove = useRemoveCartItem();
  const busy = changeQuantity.isPending || remove.isPending;

  // 수량은 절대값으로 보낸다. 증감으로 보내면 요청이 중복 도착할 때 두 번 반영된다.
  const setQuantity = (next: number) => {
    if (next < 1 || next === item.quantity) return;
    changeQuantity.mutate({ itemId: item.id, quantity: next });
  };

  const error = changeQuantity.error ?? remove.error;

  return (
    <article className={item.orderable ? styles.row : styles.rowBlocked}>
      <div className={styles.thumb} aria-hidden="true">
        <span className={styles.bar} />
      </div>

      <div className={styles.info}>
        <Link href={`/shop/product/${item.productId}`} className={styles.name}>
          {item.productName}
        </Link>
        <p className={styles.meta}>
          {item.optionName}
          {item.metalType && ` · ${METAL_LABEL[item.metalType]}`}
          {item.weightG != null && ` · ${formatGram(item.weightG)}`}
        </p>

        {item.priceChanged && item.priceDelta != null && (
          <p className={item.priceDelta > 0 ? styles.deltaUp : styles.deltaDown}>
            담을 때보다 {formatKrw(Math.abs(item.priceDelta))}{' '}
            {item.priceDelta > 0 ? '올랐습니다' : '내렸습니다'}
            <span className={styles.deltaBase}>
              (담을 때 {formatKrw(item.priceAtAdd)})
            </span>
          </p>
        )}

        {!item.orderable && item.unavailableReason && (
          <p className={styles.blocked}>{item.unavailableReason}</p>
        )}
        {error && (
          <p className={styles.blocked}>
            {error instanceof ApiError ? error.message : '처리하지 못했습니다.'}
          </p>
        )}
      </div>

      <div className={styles.qty}>
        <button
          type="button"
          aria-label="수량 줄이기"
          disabled={busy || item.quantity <= 1}
          onClick={() => setQuantity(item.quantity - 1)}
        >
          −
        </button>
        <span aria-live="polite">{item.quantity}</span>
        <button
          type="button"
          aria-label="수량 늘리기"
          disabled={busy}
          onClick={() => setQuantity(item.quantity + 1)}
        >
          +
        </button>
      </div>

      <div className={styles.amount}>
        {item.lineAmount != null ? (
          <>
            <strong>{formatKrw(item.lineAmount)}</strong>
            {/*
              「개당」 줄은 2개 이상일 때만 뜻이 있다(1개면 합계와 같은 값이다).
              그런데 그때만 그리면 수량을 1 → 2 로 올릴 때 줄이 한 칸 자라 아래가 통째로
              밀린다 — 버튼을 연달아 누르면 화면이 위아래로 출렁인다.
              그래서 자리는 늘 잡아 두고 1개일 때는 보이지만 않게 한다.
            */}
            <span
              className={
                item.quantity > 1 && item.unitPrice != null
                  ? styles.unit
                  : `${styles.unit} ${styles.unitPlaceholder}`
              }
              aria-hidden={item.quantity <= 1 || item.unitPrice == null}
            >
              개당 {formatKrw(item.unitPrice ?? item.lineAmount)}
            </span>
          </>
        ) : (
          <strong className={styles.inquiry}>{item.priceText}</strong>
        )}
      </div>

      <button
        type="button"
        className={styles.remove}
        disabled={busy}
        aria-label={`${item.productName} 삭제`}
        onClick={() => remove.mutate(item.id)}
      >
        ✕
      </button>
    </article>
  );
}

function ClearButton() {
  const clear = useClearCart();
  return (
    <div className={styles.clearWrap}>
      <button
        type="button"
        className={styles.clear}
        disabled={clear.isPending}
        onClick={() => clear.mutate(undefined)}
      >
        {clear.isPending ? '비우는 중…' : '장바구니 비우기'}
      </button>
    </div>
  );
}

function Summary({
  subtotal,
  orderable,
  unavailableCount,
  totalQuantity,
}: {
  subtotal?: number | null;
  orderable: boolean;
  unavailableCount: number;
  totalQuantity: number;
}) {
  return (
    <aside className={styles.summary} aria-label="결제 요약">
      <h2 className={styles.summaryTitle}>결제 예정 금액</h2>

      <dl className={styles.summaryRows}>
        <div>
          <dt>상품 수량</dt>
          <dd>{totalQuantity}개</dd>
        </div>
        <div>
          <dt>상품 금액</dt>
          <dd>{formatKrw(subtotal)}</dd>
        </div>
        <div>
          <dt>배송비</dt>
          <dd className={styles.later}>주문 단계에서 확정</dd>
        </div>
      </dl>

      <div className={styles.total}>
        <span>합계</span>
        <strong>{formatKrw(subtotal)}</strong>
      </div>

      {unavailableCount > 0 && (
        <p className={styles.summaryNote}>
          주문할 수 없는 {unavailableCount}건은 합계에서 빠져 있습니다.
        </p>
      )}

      {/* 주문할 수 있는 줄이 하나도 없으면 다음 화면에서 되돌아오게 된다. 여기서 막는다. */}
      {orderable ? (
        <Link className={styles.order} href="/checkout">
          주문하기
        </Link>
      ) : (
        <button type="button" className={styles.order} disabled>
          주문할 수 있는 상품이 없습니다
        </button>
      )}

      <p className={styles.disclaimer}>
        표시 금액은 현재 고시 시세 기준이며, 최종 금액은 주문하시는 시각의 시세로 확정됩니다.
        {!orderable && ' 지금은 주문할 수 있는 상품이 없습니다.'}
      </p>
    </aside>
  );
}
