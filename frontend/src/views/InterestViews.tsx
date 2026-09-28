'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { MyPageShell, myPageStyles as shell } from '@/layouts/MyPageShell';
import {
  useClearRecentlyViewed,
  useRecentlyViewed,
  useToggleWishlist,
  useWishlist,
} from '@/hooks/useInterest';
import { useAddToCart } from '@/hooks/useCart';
import { ApiError } from '@/types/api';
import type { ProductSummary } from '@/types/product';
import { formatKrw } from '@/utils/format';
import styles from '@/views/InterestViews.module.css';

/**
 * 찜한 상품 · 최근 본 상품.
 *
 * <p>금액은 <b>지금 시세로 다시 계산한</b> 값이다. 주문 내역과 반대다 — 주문은 이미 낸 돈이라
 * 고정이지만, 여기는 "지금 사면 얼마인가"를 보려고 오는 곳이다.
 */

export function WishlistView() {
  const { data, isLoading, error } = useWishlist();

  return (
    <MyPageShell title="찜한 상품" lead="금액은 지금 시세로 다시 계산한 값입니다.">
      <ProductRows
        products={data}
        isLoading={isLoading}
        error={error}
        emptyText="찜한 상품이 없습니다."
        showWishlistToggle
      />
    </MyPageShell>
  );
}

export function RecentlyViewedView() {
  const { data, isLoading, error } = useRecentlyViewed(50);
  const clear = useClearRecentlyViewed();
  const [confirming, setConfirming] = useState(false);

  return (
    <MyPageShell title="최근 본 상품" lead="최근에 열어 본 상품 50개까지 보관합니다.">
      {data && data.length > 0 && (
        <div className={styles.toolbar}>
          {confirming ? (
            <>
              <span className={styles.confirmText}>기록을 모두 지울까요?</span>
              <button
                type="button"
                className={shell.pillDark}
                disabled={clear.isPending}
                onClick={() => clear.mutate(undefined, { onSuccess: () => setConfirming(false) })}
              >
                {clear.isPending ? '지우는 중…' : '비우기'}
              </button>
              <button type="button" className={shell.pill} onClick={() => setConfirming(false)}>
                그대로 두기
              </button>
            </>
          ) : (
            <button type="button" className={shell.pill} onClick={() => setConfirming(true)}>
              기록 비우기
            </button>
          )}
        </div>
      )}

      <ProductRows
        products={data}
        isLoading={isLoading}
        error={error}
        emptyText="최근 본 상품이 없습니다."
      />
    </MyPageShell>
  );
}

function ProductRows({
  products,
  isLoading,
  error,
  emptyText,
  showWishlistToggle,
}: {
  products?: ProductSummary[];
  isLoading: boolean;
  error: unknown;
  emptyText: string;
  showWishlistToggle?: boolean;
}) {
  if (isLoading) {
    return <p className={shell.loading}>불러오는 중…</p>;
  }

  if (error) {
    return (
      <Notice tone="error" title="불러오지 못했습니다">
        {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
      </Notice>
    );
  }

  if (!products || products.length === 0) {
    return (
      <div className={shell.empty}>
        <p>{emptyText}</p>
        <div className={shell.emptyAction}>
          <Link className={shell.pillDark} href="/shop">
            상품 보러 가기
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      {products.map((product) => (
        <ProductRow key={product.id} product={product} showWishlistToggle={showWishlistToggle} />
      ))}
    </div>
  );
}

function ProductRow({
  product,
  showWishlistToggle,
}: {
  product: ProductSummary;
  showWishlistToggle?: boolean;
}) {
  const toggle = useToggleWishlist();
  const addToCart = useAddToCart();
  const [added, setAdded] = useState(false);

  // 주문 가능 여부는 서버가 이미 판단해서 내려준다. 화면에서 다시 따지지 않는다 (원칙 P1).
  const orderable = product.orderable;

  return (
    <article className={styles.row}>
      <Link href={`/shop/product/${product.id}`} className={styles.thumb} aria-hidden="true" tabIndex={-1}>
        상품
        <br />
        사진
      </Link>

      <div className={styles.info}>
        <Link href={`/shop/product/${product.id}`} className={styles.name}>
          {product.name}
        </Link>
        <div className={styles.price}>
          {product.price != null ? formatKrw(product.price) : product.priceText}
          {product.priceFrom && product.price != null && <span className={styles.from}>~</span>}
        </div>
      </div>

      <div className={styles.acts}>
        <button
          type="button"
          className={shell.pillDark}
          disabled={!orderable || addToCart.isPending}
          onClick={() =>
            addToCart.mutate(
              { productId: product.id, quantity: 1 },
              { onSuccess: () => setAdded(true) },
            )
          }
        >
          {added ? '담았습니다' : '장바구니 담기'}
        </button>

        {showWishlistToggle && (
          <button
            type="button"
            className={shell.pill}
            disabled={toggle.isPending}
            onClick={() => toggle.mutate({ productId: product.id, next: false })}
          >
            찜 해제
          </button>
        )}
      </div>
    </article>
  );
}
