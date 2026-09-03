import { PRICE_INQUIRY_LABEL, formatKrw } from '@/utils/format';
import type { MarketPrice, MetalType } from '@/types/price';
import styles from './PriceTicker.module.css';

/** 시안의 티커에 노출되는 금속. 14K 는 빠져 있다. */
const TICKER_METALS: MetalType[] = ['GOLD_24K', 'GOLD_18K', 'PLATINUM', 'SILVER'];

interface Props {
  prices: MarketPrice[];
  /** 표시 기준 날짜 문구. 없으면 생략 */
  quotedAt: string | null;
}

/**
 * 히어로 아래 다크 시세 스트립 (레퍼런스 시안 `.price-strip`).
 *
 * <p>고객이 사는 가격(SELL)을 보여준다. 1돈(3.75g) 기준인 이유는 이 업종의 고객이
 * 그 단위로 가격을 기억하기 때문이다 — 시안도 같은 기준이다.
 *
 * <p>고시되지 않은 금속은 금액 자리에 "시세문의"가 들어간다. 0 이 그려지는 경로가 없다.
 */
export function PriceTicker({ prices, quotedAt }: Props) {
  const rows = TICKER_METALS.map((metal) => ({
    metal,
    price: prices.find((p) => p.metalType === metal && p.priceSide === 'SELL'),
  })).filter((row) => row.price != null);

  return (
    <div className={styles.strip}>
      <div className="wrap">
        <div className={styles.label}>
          오늘의 시세
          <span>{quotedAt ? `${quotedAt} 기준` : '고시 대기'}</span>
        </div>

        <div className={styles.ticker}>
          {rows.length === 0 ? (
            <div className={styles.tick}>
              <span className={styles.name}>시세를 불러올 수 없습니다</span>
            </div>
          ) : (
            rows.map(({ metal, price }) => (
              <div key={metal} className={styles.tick}>
                <span className={styles.name}>{price!.metalName}</span>
                {price!.pricePerDon != null ? (
                  <span className={`${styles.val} tabular`}>{formatKrw(price!.pricePerDon)}</span>
                ) : (
                  <span className={styles.inquiry}>{PRICE_INQUIRY_LABEL}</span>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
