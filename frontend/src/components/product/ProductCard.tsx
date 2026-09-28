import Link from 'next/link';

import { WishlistHeart } from '@/components/product/WishlistHeart';
import { PRICE_INQUIRY_LABEL, formatKrw } from '@/utils/format';
import { METAL_LABEL } from '@/types/price';
import { PRODUCT_STATUS_LABEL, type ProductSummary } from '@/types/product';
import styles from './ProductCard.module.css';

interface Props {
  product: ProductSummary;
  /** NEW / BEST 뱃지. 시안의 `.flag` */
  flag?: 'new' | 'best';
}

/**
 * 상품 카드 (레퍼런스 시안 `.prod`).
 *
 * <p>썸네일은 시안의 CSS 금괴 일러스트를 그대로 쓴다. 실제 상품 사진은 Object Storage
 * 연동(STEP 10) 후에 들어오는데, 그 전까지 회색 네모를 두면 화면이 비어 보인다.
 * 금속 종류에 따라 금색/은색으로 갈리고, 반지·기념품처럼 중량이 작은 것은 원형이다.
 *
 * <p>가격은 서버가 계산한 값을 표시만 한다. 시세×중량을 여기서 계산하면 목록·상세·결제
 * 금액이 서로 어긋날 수 있다 (설계서 원칙 P1).
 */
export function ProductCard({ product, flag }: Props) {
  const blockedLabel = blockedReason(product);

  return (
    // 하트는 링크 <b>바깥</b>에 둔다 — 링크 안의 버튼은 누르면 링크가 함께 열린다.
    <div className={styles.card}>
      <WishlistHeart productId={product.id} productName={product.name} />
      <Link href={`/shop/product/${product.id}`} className={styles.prod}>
      <div className={styles.thumb}>
        {flag && (
          <span className={flag === 'new' ? styles.flagNew : styles.flagBest}>
            {flag.toUpperCase()}
          </span>
        )}
        <div className={barClassName(product)}>{barLabel(product)}</div>
        {blockedLabel && (
          <span className={styles.soldOut}>
            <b className={styles.soldOutLabel}>{blockedLabel}</b>
          </span>
        )}
      </div>

      <div className={styles.info}>
        <div className={styles.cat}>{categoryLabel(product)}</div>
        <h5>{product.name}</h5>
        <div className={styles.price}>
          {product.price != null ? (
            <>
              <span className="tabular">{formatKrw(product.price)}</span>
              {product.priceFrom && <span className={styles.from}>~</span>}
            </>
          ) : (
            <span className={styles.inquiry}>{PRICE_INQUIRY_LABEL}</span>
          )}
          <small>{product.price != null ? '시세 반영가' : '고시 후 표시'}</small>
        </div>
      </div>
      </Link>
    </div>
  );
}

/** 은은 은색 바, 소형(3g 미만)은 원형 — 시안의 `.bar.silver` / `.bar.round`. */
/**
 * 썸네일을 덮는 막에 적을 문구. 덮지 않아야 하면 null.
 *
 * <p>`orderable` 하나로 판단하지 않는다. 이 값은 품절·판매종료뿐 아니라
 * <b>시세가 없거나 오래되어 값을 확정할 수 없을 때</b>도 false 가 된다
 * (설계서 P6 — 값을 0 이나 NULL 로 만들지 않고 "시세문의"로 물러난다).
 * 그 경우까지 "품절"을 찍으면, 가격 줄에는 "시세문의"라고 적혀 있는데
 * 사진 위에는 "품절"이라고 적힌 카드가 나온다. 두 문구가 서로 다른 말을
 * 하면 사용자는 어느 쪽도 믿지 않는다.
 *
 * <p>재고 문제가 아닌 경우에는 덮지 않는다 — 가격 줄의 "시세문의"가
 * 이미 살 수 없는 이유를 말하고 있다.
 */
function blockedReason(product: ProductSummary): string | null {
  if (product.status === 'SOLD_OUT') return PRODUCT_STATUS_LABEL.SOLD_OUT;
  if (product.status === 'DISCONTINUED') return PRODUCT_STATUS_LABEL.DISCONTINUED;
  return null;
}

function barClassName(product: ProductSummary): string {
  if (product.metalType === 'SILVER') return `${styles.bar} ${styles.barSilver}`;
  if (product.weightG != null && product.weightG < 3) return `${styles.bar} ${styles.barRound}`;
  return styles.bar;
}

function barLabel(product: ProductSummary): string {
  switch (product.metalType) {
    case 'SILVER':
      return 'SILVER 999';
    case 'GOLD_24K':
      return 'GOLD 999.9';
    case 'GOLD_18K':
      return 'GOLD 750';
    case 'GOLD_14K':
      return 'GOLD 585';
    case 'PLATINUM':
      return 'PT 950';
    default:
      return '한성';
  }
}

function categoryLabel(product: ProductSummary): string {
  return product.metalType ? METAL_LABEL[product.metalType] : '기타';
}
