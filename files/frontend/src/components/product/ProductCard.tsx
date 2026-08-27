import Link from 'next/link';
import { PRICE_INQUIRY_LABEL, formatKrw } from '@/utils/format';
import { METAL_LABEL } from '@/types/price';
import type { ProductSummary } from '@/types/product';
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
  const soldOut = product.status === 'SOLD_OUT' || !product.orderable;

  return (
    <Link href={`/shop/product/${product.id}`} className={styles.prod}>
      <div className={styles.thumb}>
        {flag && (
          <span className={flag === 'new' ? styles.flagNew : styles.flagBest}>
            {flag.toUpperCase()}
          </span>
        )}
        <div className={barClassName(product)}>{barLabel(product)}</div>
        {soldOut && <span className={styles.soldOut}>품절</span>}
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
  );
}

/** 은은 은색 바, 소형(3g 미만)은 원형 — 시안의 `.bar.silver` / `.bar.round`. */
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
