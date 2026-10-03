'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { PriceTag } from '@/components/ui/PriceTag';
import { WishlistHeart } from '@/components/product/WishlistHeart';
import { useAddToCart } from '@/hooks/useCart';
import { useMarkViewed } from '@/hooks/useInterest';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import { METAL_LABEL } from '@/types/price';
import type { ProductDetail } from '@/types/product';
import { formatGram, formatKrw, formatPricePerGram } from '@/utils/format';
import styles from './ProductDetailView.module.css';

/**
 * 상품 상세 (설계서 §9 — {@code /shop/product/{id}}).
 *
 * <p>클라이언트 컴포넌트지만 <b>데이터는 서버가 이미 채워서 넘긴다.</b> 초기 HTML 에
 * 상품명·가격·내역이 모두 들어 있어 검색엔진이 읽을 수 있고, 옵션 전환만 브라우저에서
 * 처리된다. 옵션을 바꿀 때마다 서버를 다시 부르지 않는 이유는 <b>옵션별 가격이 이미
 * 응답에 들어 있기</b> 때문이다.
 *
 * <p>가격 내역은 서버가 계산한 구성요소를 그대로 나열한다. 여기서 더하거나 곱하지 않는다 —
 * 합계는 이미 서버에서 맞춰져 있고, 프론트엔드가 다시 계산하면 두 값이 어긋날 수 있다
 * (설계서 원칙 P1).
 */
export function ProductDetailView({ product }: { product: ProductDetail }) {
  const router = useRouter();
  const { restored, isAuthenticated } = useUserSession();
  const addToCart = useAddToCart();
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [added, setAdded] = useState(false);

  /**
   * "최근 본 상품"에 남긴다.
   *
   * <p>회원일 때만, 이 화면에서 <b>한 번만</b> 부른다. 옵션을 바꿀 때마다 부르면 같은
   * 상품을 여러 번 기록하게 되고, 실패해도 화면에는 아무 일도 일어나지 않는다.
   */
  const markViewed = useMarkViewed();
  const marked = useRef(false);
  useEffect(() => {
    if (!isAuthenticated || marked.current) return;
    marked.current = true;
    markViewed.mutate(product.id);
  }, [isAuthenticated, product.id, markViewed]);

  const option = product.options[selectedIndex];
  const price = product.prices[selectedIndex];
  const orderable = price?.orderable ?? false;

  /**
   * 장바구니 담기.
   *
   * <p>수량과 옵션만 보낸다 — 가격은 서버가 담는 순간 계산해 기록한다 (원칙 P1).
   * 비회원이면 로그인으로 보내되, 로그인 후 이 상품으로 돌아오게 next 를 실어 준다.
   */
  const handleAddToCart = (then?: () => void) => {
    if (restored && !isAuthenticated) {
      const next = encodeURIComponent(`/shop/product/${product.id}`);
      router.push(`/login?reason=required&next=${next}`);
      return;
    }
    setAdded(false);
    addToCart.mutate(
      { productId: product.id, optionId: option?.id ?? null, quantity: 1 },
      { onSuccess: () => (then ? then() : setAdded(true)) },
    );
  };

  /**
   * 바로 구매 — 담고 주문서로 보낸다.
   *
   * <p>⚠️ 주문서는 <b>장바구니 전체</b>를 주문으로 만든다. 그래서 장바구니에 다른
   * 상품이 남아 있으면 그것도 함께 주문된다. 이 상품 하나만 사는 흐름은 서버에
   * 별도 경로가 필요하다(지금 주문 API 는 금액·상품을 받지 않고 장바구니를 읽는다).
   * 그때까지는 "담고 바로 주문서로" 다.
   */
  const handleBuyNow = () => handleAddToCart(() => router.push('/checkout'));

  return (
    <div className="wrap">
      <nav className={styles.crumb} aria-label="위치">
        <Link href="/shop">상품</Link>
        <span aria-hidden="true">›</span>
        <span>{product.name}</span>
      </nav>

      <div className={styles.layout}>
        {/* ---------- 이미지 ---------- */}
        <div className={styles.gallery}>
          <WishlistHeart productId={product.id} productName={product.name} />
          <div className={styles.mainImage} aria-hidden="true">
            <span className={styles.imageMark}>
              {product.metalType ? METAL_LABEL[product.metalType] : '한성금거래소'}
            </span>
          </div>
          {product.imageKeys.length > 0 && (
            <p className={styles.imageNote}>
              이미지 {product.imageKeys.length}장 · 스토리지 연동 후 표시됩니다
            </p>
          )}
        </div>

        {/* ---------- 정보 ---------- */}
        <div className={styles.info}>
          <p className={styles.meta}>
            {product.metalType && <span>{METAL_LABEL[product.metalType]}</span>}
            {product.purity != null && <span>순도 {product.purity}</span>}
          </p>

          <h1 className={styles.name}>{product.name}</h1>

          <div className={styles.priceBox}>
            <PriceTag price={price?.price ?? null} size="lg" />
            {price?.marketPricePerGram != null && (
              <p className={styles.basis}>
                적용 시세 {formatPricePerGram(price.marketPricePerGram)}
              </p>
            )}
            {price?.notice && <p className={styles.notice}>{price.notice}</p>}
          </div>

          {/* ---------- 옵션 ---------- */}
          {product.options.length > 0 && (
            <div className={styles.options}>
              <p className={styles.optionLabel}>옵션 선택</p>
              <div className={styles.optionList} role="radiogroup" aria-label="옵션 선택">
                {product.options.map((opt, index) => {
                  const optPrice = product.prices[index];
                  const selected = index === selectedIndex;
                  return (
                    <button
                      key={opt.id}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      disabled={!opt.inStock}
                      onClick={() => {
                        setSelectedIndex(index);
                        setAdded(false);
                      }}
                      className={selected ? styles.optionOn : styles.option}
                    >
                      <span className={styles.optionName}>{opt.name}</span>
                      <span className={styles.optionPrice}>
                        {optPrice?.price != null
                          ? formatKrw(optPrice.price)
                          : optPrice?.displayText}
                      </span>
                      {!opt.inStock && <span className={styles.optionSoldOut}>품절</span>}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* ---------- 가격 내역 ---------- */}
          {price && price.components.length > 0 && (
            <div className={styles.breakdown}>
              <p className={styles.breakdownTitle}>가격 구성</p>
              <dl className={styles.breakdownList}>
                {price.components.map((c) => (
                  <div key={`${c.sort}-${c.label}`}>
                    <dt>
                      {c.label}
                      {c.unitPricePerGram != null && c.weightG != null && (
                        <span className={styles.formula}>
                          {formatPricePerGram(c.unitPricePerGram)} × {formatGram(c.weightG)}
                        </span>
                      )}
                    </dt>
                    <dd className="tabular">{formatKrw(c.amount)}</dd>
                  </div>
                ))}
              </dl>
              <p className={styles.breakdownNote}>
                구성 금액의 합계가 판매가입니다. 반올림은 최종 합계에 한 번만 적용됩니다.
              </p>
            </div>
          )}

          {/* ---------- 구매 ---------- */}
          <div className={styles.actions}>
            <button
              type="button"
              className={styles.cartBtn}
              disabled={!orderable || addToCart.isPending}
              onClick={() => handleAddToCart()}
            >
              {addToCart.isPending ? '담는 중…' : '장바구니 담기'}
            </button>
            <button
              type="button"
              className={styles.buyBtn}
              disabled={!orderable || addToCart.isPending}
              onClick={handleBuyNow}
            >
              바로 구매
            </button>
          </div>

          {added && (
            <p className={styles.added} role="status">
              장바구니에 담았습니다. <Link href="/cart">장바구니 보기</Link>
            </p>
          )}
          {addToCart.error && (
            <p className={styles.blocked} role="alert">
              {addToCart.error instanceof ApiError
                ? addToCart.error.message
                : '장바구니에 담지 못했습니다.'}
            </p>
          )}

          {!orderable && (
            <p className={styles.blocked}>
              {price?.status === 'SOLD_OUT'
                ? '선택하신 옵션은 품절입니다.'
                : '현재 시세를 확인할 수 없어 주문이 제한됩니다. 시세문의 후 이용해 주세요.'}
            </p>
          )}
          <p className={styles.disclaimer}>
            최종 결제 금액은 주문 시점에 서버가 다시 계산합니다. 화면 금액과 결제 금액이 다를 수
            없습니다.
          </p>
        </div>
      </div>

      {product.description && (
        <section className={styles.description} aria-labelledby="product-desc">
          <h2 id="product-desc" className={styles.descTitle}>
            상품 설명
          </h2>
          <p className={styles.descBody}>{product.description}</p>
        </section>
      )}

      {option && (
        <p className={styles.specNote}>
          선택 옵션 {option.name}
          {option.weightG != null && ` · ${formatGram(option.weightG)}`}
        </p>
      )}
    </div>
  );
}
