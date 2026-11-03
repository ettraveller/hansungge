'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { PriceTag } from '@/components/ui/PriceTag';
import { Reveal } from '@/components/ui/Reveal';
import { DeliveryTerms } from '@/components/product/DeliveryTerms';
import { ProductTabs } from '@/components/product/ProductTabs';
import { WishlistHeart } from '@/components/product/WishlistHeart';
import { useAddToCart, useCart } from '@/hooks/useCart';
import { useMarkViewed } from '@/hooks/useInterest';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import { METAL_LABEL } from '@/types/price';
import type { RefundPolicy } from '@/api/policies';
import type { QnaSummary } from '@/types/board';
import type { ProductDetail, ProductSummary } from '@/types/product';
import { formatGram, formatKrw, formatPurity } from '@/utils/format';
import { mediaUrl } from '@/utils/media';
import styles from './ProductDetailView.module.css';

/**
 * 한 번에 담을 수 있는 최대 개수.
 *
 * <p>백엔드 {@code CartItem.MAX_QUANTITY} 와 같은 값이다. 상품별로 더 낮은 상한
 * ({@code products.max_qty})이 걸려 있을 수 있는데 그 값은 공개 응답에 없다 — 넘으면
 * 서버가 "이 상품은 최대 N개까지 담을 수 있습니다"로 막고, 그 문구를 아래 오류 자리에
 * 그대로 띄운다.
 */
const MAX_QTY = 99;

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
export function ProductDetailView({
  product,
  qnas = [],
  qnaTotal = 0,
  related = [],
  refundPolicy = null,
  preview = false,
}: {
  product: ProductDetail;
  /** 이 상품의 문의 앞쪽 몇 건. 서버가 채워서 넘긴다 */
  qnas?: QnaSummary[];
  qnaTotal?: number;
  /** 같은 분류의 다른 상품 */
  related?: ProductSummary[];
  /** 구매정보 탭에 그대로 싣는 환불규정 현재 판 */
  refundPolicy?: RefundPolicy | null;
  /**
   * 관리자 미리보기로 그리는 중인가.
   *
   * <p>true 면 <b>바깥을 건드리는 동작을 전부 끈다</b> — 아직 상품이 아니라서
   * 장바구니·찜·최근본상품이 가리킬 id 가 없다. 그대로 두면 없는 상품으로
   * 요청이 나가 404 가 나고, 최근 본 상품에는 엉뚱한 기록이 남는다.
   *
   * <p>화면은 그대로 그린다. 그것이 미리보기의 목적이다.
   */
  preview?: boolean;
}) {
  const router = useRouter();
  const { restored, isAuthenticated } = useUserSession();
  const addToCart = useAddToCart();
  // 미리보기에서는 부르지 않는다 -- 관리자 화면이고, 담을 대상도 없다.
  const cart = useCart();
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [added, setAdded] = useState(false);
  const [photoIndex, setPhotoIndex] = useState(0);
  const [qty, setQty] = useState(1);

  /**
   * 위쪽 갤러리 사진.
   *
   * <p>목록에서 눌러 들어왔으므로 첫 장이 그 사진이어야 한다 — 서버가 대표이미지
   * 목록 순서대로 주고, 그 첫 장이 {@code thumbnailKey} 다. 아직 아무것도 올리지
   * 않은 상품은 비어 있고, 그때는 일러스트 자리표시자를 쓴다.
   */
  const photos = product.galleryKeys
    .map((key) => mediaUrl(key))
    .filter((url): url is string => url != null);

  /**
   * 실제로 보여줄 사진 번호.
   *
   * <p>{@link photoIndex} 를 그대로 믿지 않는다 — 관리자가 사진을 지운 뒤라면 목록이
   * 줄어 있어서, 고른 번호가 범위를 넘으면 {@code undefined} 를 src 에 넣게 된다.
   * 넘으면 마지막 장으로 민다. 범위를 거르는 곳을 하나로 둬야 큰 사진·썸네일 줄·
   * 좌우 버튼이 같은 장을 가리킨다.
   */
  const shown = photos.length === 0 ? 0 : Math.min(photoIndex, photos.length - 1);

  /**
   * "최근 본 상품"에 남긴다.
   *
   * <p>회원일 때만, 이 화면에서 <b>한 번만</b> 부른다. 옵션을 바꿀 때마다 부르면 같은
   * 상품을 여러 번 기록하게 되고, 실패해도 화면에는 아무 일도 일어나지 않는다.
   */
  const markViewed = useMarkViewed();
  const marked = useRef(false);
  useEffect(() => {
    // 미리보기에는 기록할 상품이 없다. 그대로 두면 없는 id 로 요청이 나간다.
    if (preview || !isAuthenticated || marked.current) return;
    marked.current = true;
    markViewed.mutate(product.id);
  }, [preview, isAuthenticated, product.id, markViewed]);

  const option = product.options[selectedIndex];
  const price = product.prices[selectedIndex];
  const orderable = price?.orderable ?? false;

  // 옵션마다 중량이 다르다(3.75g / 7.5g). 고른 옵션 쪽을 먼저 보여 준다.
  const weightG = option?.weightG ?? product.weightG;

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
      { productId: product.id, optionId: option?.id ?? null, quantity: qty },
      { onSuccess: () => (then ? then() : setAdded(true)) },
    );
  };

  /**
   * 이 옵션이 이미 장바구니에 있는가.
   *
   * <p>「바로 구매」가 또 담지 않게 하려고 본다. 담기는 <b>기존 수량에 더하는</b>
   * 동작이라({@code CartService.add}), 재고가 1개인 상품을 담아 둔 채로 바로 구매를
   * 누르면 2개가 되어 "재고가 부족합니다"가 뜬다 — 재고는 멀쩡한데 못 사게 된다.
   */
  const cartLine = cart.data?.items.find(
    (line) => line.productId === product.id && line.optionId === (option?.id ?? null),
  );
  const inCart = cartLine != null;

  /**
   * 바로 구매 — 담고 주문서로 보낸다.
   *
   * <p>⚠️ 주문서는 <b>장바구니 전체</b>를 주문으로 만든다. 그래서 장바구니에 다른
   * 상품이 남아 있으면 그것도 함께 주문된다. 이 상품 하나만 사는 흐름은 서버에
   * 별도 경로가 필요하다(지금 주문 API 는 금액·상품을 받지 않고 장바구니를 읽는다).
   * 그때까지는 "담고 바로 주문서로" 다.
   *
   * <p><b>이미 담겨 있으면 또 담지 않는다.</b> 담기는 기존 수량에 더하는 동작이라,
   * 재고가 1개인 상품을 담아 둔 채로 누르면 2개가 되어 "재고가 부족합니다"로 막힌다.
   * 재고는 멀쩡한데 못 사게 되는 것이라, 고객 입장에서는 품절과 구분되지 않는다.
   *
   * <p>수량을 더 사고 싶으면 장바구니에서 늘린다. 「바로 구매」는 "이거 사러 가자"는
   * 뜻이지 "하나 더"가 아니다.
   */
  const handleBuyNow = () => {
    if (restored && !isAuthenticated) {
      const next = encodeURIComponent(`/shop/product/${product.id}`);
      router.push(`/login?reason=required&next=${next}`);
      return;
    }
    if (inCart) {
      router.push('/checkout');
      return;
    }
    handleAddToCart(() => router.push('/checkout'));
  };

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
          {!preview && <WishlistHeart productId={product.id} productName={product.name} />}
          {photos.length > 0 ? (
            <>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                className={styles.mainPhoto}
                src={photos[shown]}
                alt={product.name}
                // 첫 화면에 바로 보이는 그림이라 지연 로딩을 걸지 않는다. 걸면
                // 가장 큰 요소가 늦게 그려져 LCP 가 그만큼 밀린다.
                loading="eager"
                decoding="async"
              />

              {/*
                좌우 넘김. 아래 썸네일 줄만 있으면 사진이 여러 장이라는 것을
                알아채지 못하고 첫 장만 보고 나가는 경우가 많다.

                ⚠️ 사진 <b>위에 얹지 않는다.</b> 상세는 상품을 확인하는 자리인데,
                반투명 버튼이라도 그 자리를 덮으면 하필 가장자리의 특징(잠금쇠,
                각인)을 가린다. 사진 아래 한 줄로 내린다.

                끝에서 처음으로 돌아간다. 막아 두면 마지막 장에서 오른쪽 버튼이
                회색이 되는데, 그때 "더 없다"가 아니라 "고장났다"로 읽힌다.
              */}
              {photos.length > 1 && (
                <div className={styles.photoNav}>
                  <button
                    type="button"
                    className={styles.navBtn}
                    aria-label="이전 사진"
                    onClick={() => setPhotoIndex((shown - 1 + photos.length) % photos.length)}
                  >
                    ‹
                  </button>
                  {/* 버튼만 있으면 끝까지 눌러 봐야 몇 장인지 알 수 있다. */}
                  <span className={styles.photoCount}>
                    {shown + 1} / {photos.length}
                  </span>
                  <button
                    type="button"
                    className={styles.navBtn}
                    aria-label="다음 사진"
                    onClick={() => setPhotoIndex((shown + 1) % photos.length)}
                  >
                    ›
                  </button>
                </div>
              )}

              {photos.length > 1 && (
                <ul className={styles.photoStrip}>
                  {photos.map((url, index) => (
                    <li key={url}>
                      <button
                        type="button"
                        className={
                          index === shown ? styles.photoPickerOn : styles.photoPicker
                        }
                        aria-label={`${index + 1}번째 사진 보기`}
                        aria-current={index === shown}
                        onClick={() => setPhotoIndex(index)}
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img src={url} alt="" loading="lazy" decoding="async" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <div className={styles.mainImage} aria-hidden="true">
              <span className={styles.imageMark}>
                {product.metalType ? METAL_LABEL[product.metalType] : '한성금거래소'}
              </span>
            </div>
          )}
        </div>

        {/* ---------- 정보 ---------- */}
        <div className={styles.info}>
          <h1 className={styles.name}>{product.name}</h1>

          {/*
            적용 시세와 가격 구성(시세×중량 + 공임)을 화면에서 뺐다.

            금액이 어떻게 나왔는지를 보여주면 신뢰가 오른다고 보고 넣었던 것인데,
            같은 화면이 <b>공임을 그대로 공개한다.</b> 공임은 상품마다 다르게 매기는
            값이라 그대로 내걸면 상품끼리 비교당하고, 경쟁사가 마진을 그대로 읽는다.

            ⚠️ 화면에서만 뺀 것이다. 공개 API 는 여전히 components 와 시세를 돌려주므로
            개발자도구를 열면 보인다. 값 자체를 가리려면 백엔드 응답에서 빼야 한다.
          */}
          <div className={styles.priceBox}>
            <div className={styles.priceLine}>
              <span className={styles.priceLabel}>판매가</span>
              <PriceTag price={price?.price ?? null} size="lg" />
              {/* 아래에서 개수를 고를 수 있으니, 이 값이 한 개 값이라는 것을 적어 둔다 */}
              {price?.price != null && <span className={styles.priceUnit}>1개</span>}
            </div>
            {price?.notice && <p className={styles.notice}>{price.notice}</p>}
          </div>

          {/*
            사양을 표로 세운다. 금종·순도·중량이 작은 회색 한 줄이었는데, 금을 살 때
            고객이 금액 다음으로 확인하는 것이 이 세 가지다. 중량은 고른 옵션의 것을
            보여 준다 — 3.75g 과 7.5g 중 어느 쪽을 고른 상태인지가 금액의 근거다.

            값이 없는 줄은 그리지 않는다. "중량 -" 은 중량을 모른다는 뜻으로 읽히는데,
            실제로는 중량으로 팔지 않는 상품(반지 등)일 뿐이다.
          */}
          {/* 세 줄 다 없는 상품이면 표 자체를 그리지 않는다 — 안 그리면 테두리 두 줄만
              남아서 뭔가 빠진 자리처럼 보인다 */}
          {(product.metalType != null || product.purity != null || weightG != null) && (
          <dl className={styles.specs}>
            {product.metalType && (
              <div>
                <dt>금종</dt>
                <dd>{METAL_LABEL[product.metalType]}</dd>
              </div>
            )}
            {product.purity != null && (
              <div>
                <dt>순도</dt>
                <dd>{formatPurity(product.purity)}</dd>
              </div>
            )}
            {weightG != null && (
              <div>
                <dt>중량</dt>
                <dd>{formatGram(weightG)}</dd>
              </div>
            )}
          </dl>
          )}

          {/* ---------- 옵션 ---------- */}
          {/*
            고를 것이 있을 때만 그린다. 상품을 등록하면 서버가 기본 옵션을 하나
            자동으로 만들어서, 옵션이 없는 상품도 「옵션 선택 — 상품명 · 1,587,400원」
            이라는 줄이 하나 떴다. 상품명과 금액을 바로 위에서 이미 읽은 사람에게
            같은 두 값을 다시 보여 주면서 고르라고 하는 꼴이다.

            고른 옵션(index 0)은 그대로다 — 숨긴 것은 화면뿐이다.
          */}
          {product.options.length > 1 && (
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
                        // 옵션마다 재고와 상한이 다르다. 3.75g 를 열 개 고른 상태로
                        // 37.5g 로 옮기면 사려던 양이 아니다.
                        setQty(1);
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

          {/* ---------- 수량 ---------- */}
          {/*
            개수를 고를 수 있게 한다. 1개씩만 담기던 동안 1g 골드바 열 개를 사려면
            담기를 열 번 눌러야 했다.

            ⚠️ <b>여기서 합계를 그리지 않는다.</b> 단가 × 수량을 화면에서 곱하면 금액을
            정하는 곳이 둘이 되고, 둘은 반드시 어긋난다 (원칙 P1). 합계는 담은 뒤
            장바구니·주문서가 서버가 계산한 값으로 보여 준다.
          */}
          <div className={styles.qtyRow}>
            <span className={styles.qtyLabel}>수량</span>
            <div className={styles.qty}>
              <button
                type="button"
                aria-label="수량 줄이기"
                disabled={preview || qty <= 1}
                onClick={() => setQty((n) => Math.max(1, n - 1))}
              >
                −
              </button>
              <span aria-live="polite">{qty}</span>
              <button
                type="button"
                aria-label="수량 늘리기"
                disabled={preview || qty >= MAX_QTY}
                onClick={() => setQty((n) => Math.min(MAX_QTY, n + 1))}
              >
                +
              </button>
            </div>
          </div>

          {/*
            이미 담겨 있다는 사실을 알린다. 「바로 구매」가 이때 <b>다시 담지 않고</b>
            주문서로 넘어가기 때문이다 — 그 이유를 적어 두지 않으면 수량을 3으로 맞춰
            눌렀는데 주문서에 1개가 떠 있는 것으로 보인다.
          */}
          {!preview && cartLine && (
            <p className={styles.inCart}>
              이미 장바구니에 {cartLine.quantity}개 담겨 있습니다. 개수는{' '}
              <Link href="/cart">장바구니</Link>에서 바꿉니다.
            </p>
          )}

          {/* ---------- 구매 ---------- */}
          {/*
            미리보기에서도 버튼은 그린다 — 고객이 보는 화면이 어떤 모양인지가 미리보기의
            목적이고, 버튼을 빼면 그 아래 여백과 배치가 달라진다. 대신 누르지 못하게 한다.
            아직 상품이 아니라서 담을 대상이 없다.
          */}
          <div className={styles.actions}>
            <button
              type="button"
              className={styles.cartBtn}
              disabled={preview || !orderable || addToCart.isPending}
              onClick={() => handleAddToCart()}
            >
              {addToCart.isPending ? '담는 중…' : '장바구니 담기'}
            </button>
            <button
              type="button"
              className={styles.buyBtn}
              disabled={preview || !orderable || addToCart.isPending}
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
                ? // 옵션 줄을 감춘 상품에 "선택하신 옵션"이라고 적으면 고르지도 않은
                  // 것을 가리키게 된다
                  product.options.length > 1
                  ? '선택하신 옵션은 품절입니다.'
                  : '품절된 상품입니다.'
                : '현재 시세를 확인할 수 없어 주문이 제한됩니다. 시세문의 후 이용해 주세요.'}
            </p>
          )}
          {/*
            배송기간을 상품마다 적는다. 사이트 공통 안내로 한 줄 두면 "이 상품이
            어느 쪽인지"를 고객이 알 수 없고, 주문제작의 청약철회 제한은 상품별로
            고지해야 성립한다 (전자상거래법 §17②①). 금액 바로 아래에 둔다 —
            살지 말지를 정하는 자리에 기간이 함께 있어야 한다.
          */}
          <DeliveryTerms madeToOrder={product.madeToOrder} />

          {/*
            무통장 전용 상품임을 담기 전에 알린다.

            순금·순은·골드바와 24K, 시세에 연동되는 상품은 카드사가 온라인 거래를
            제한해 카드·간편결제를 쓸 수 없다 (2026-11-03 토스페이먼츠 회신).
            주문서까지 가서야 알면, 카드로 사려던 사람은 거기서 멈추고 돌아간다 —
            장바구니에 담고 배송지까지 적은 뒤다.

            이유를 함께 적는다. 「무통장만 됩니다」만 내걸면 가게 사정으로 읽히고,
            금은방이 카드를 피한다는 오래된 인상과 겹친다.
          */}
          {!product.cardPayable && (
            <p className={styles.bankOnly}>
              <strong>무통장입금 전용 상품입니다.</strong> 순금·순은과 시세에 연동되는
              상품은 카드사의 온라인 거래 제한으로 카드·간편결제를 이용할 수 없습니다.
            </p>
          )}

          {/*
            "최종 결제 금액은 주문 시점에 서버가 다시 계산합니다"를 뺐다.

            고객에게는 당연한 일이고(어디서 사든 그렇다), 굳이 적으면 "금액이 달라질
            수도 있다는 말인가" 하고 되읽게 된다. 안심시키려던 문장이 반대로 읽혔다.

            그 성질 자체는 그대로다 — 금액은 서버가 정한다 (원칙 P1).
          */}
        </div>
      </div>

      {/*
        상품 설명·구매 안내·문의·관련상품을 탭으로 나눈다. 세로로 이어 붙이면
        상세 이미지가 긴 상품에서 문의가 화면 다섯 장 아래로 밀리는데, 사기 전에
        가장 자주 찾는 것이 그 문의다. 관리자 미리보기에서는 바깥 데이터가 없어
        설명 탭만 의미가 있다 — 그래도 같은 모양으로 보여 준다.
      */}
      {/*
        여기부터 접힌 화면 아래다. 올라오며 들어온다.

        ⚠️ 위쪽(사진·상품명·판매가·수량)에는 걸지 않는다. 들어오자마자 보이는
        자리라 드러낼 것이 없고, 무엇보다 <b>판매가가 떠오르며 나타나면 값이
        방금 바뀐 것처럼</b> 읽힌다. 시세에 연동된 금을 파는 화면에서 그 한
        순간의 착각이 제일 나쁘다 — 메인 시세표를 가만히 둔 것과 같은 이유다.
      */}
      <Reveal>
        <ProductTabs
          product={product}
          qnas={qnas}
          qnaTotal={qnaTotal}
          related={related}
          refundPolicy={refundPolicy}
        />
      </Reveal>

    </div>
  );
}
