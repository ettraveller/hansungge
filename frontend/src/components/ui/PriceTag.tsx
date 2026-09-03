import { PRICE_INQUIRY_LABEL, formatKrw } from '@/utils/format';
import styles from './PriceTag.module.css';

type Size = 'sm' | 'md' | 'lg';

interface Props {
  /** 서버가 계산한 금액. null 이면 표시할 수 없는 상태다 */
  price: number | null;
  /** 옵션이 여럿이라 "○○원~" 으로 표시해야 하는가 */
  from?: boolean;
  size?: Size;
  /** 표시 불가 시 문구. 기본은 "시세문의" */
  fallback?: string;
}

/**
 * 금액 표시 — <b>0원이 그려질 수 없는 컴포넌트</b>.
 *
 * 가격이 없으면 `0` 이 아니라 "시세문의"를 표시한다. 이 프로젝트에서 가장 피해야 할
 * 화면이 "0원"이고(설계서 §12.4), 그것을 막는 마지막 지점이 여기다.
 *
 * 금액 계산은 하지 않는다. 서버가 준 값을 포맷만 한다 (원칙 P1).
 */
export function PriceTag({ price, from = false, size = 'md', fallback = PRICE_INQUIRY_LABEL }: Props) {
  if (price == null) {
    return <span className={`${styles.inquiry} ${styles[size]}`}>{fallback}</span>;
  }

  return (
    <span className={`${styles.price} ${styles[size]} tabular`}>
      {formatKrw(price)}
      {from && <span className={styles.from}>~</span>}
    </span>
  );
}
