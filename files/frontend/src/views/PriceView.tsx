import type { Loaded } from '@/api/server';
import { MarketPriceTable } from '@/components/price/MarketPriceTable';
import { PriceTicker } from '@/components/price/PriceTicker';
import type { MarketPrice } from '@/types/price';
import { formatDateTime, formatPricePerGram } from '@/utils/format';
import styles from './PriceView.module.css';

/**
 * 금시세 화면 (`/price`) — 메인의 시세 섹션을 확장한 형태다.
 *
 * <p>레퍼런스 시안에는 별도 시세 페이지가 없고 메인 앵커(`#quote`)만 있다. 상세 페이지를
 * 따로 두는 이유는 <b>SEO</b> 다 — "금시세"는 이 업종에서 검색량이 가장 큰 키워드이고,
 * 앵커는 독립 URL 이 아니라 색인되지 않는다. 시각 언어는 시안 그대로 유지한다.
 */
export function PriceView({ prices }: { prices: Loaded<MarketPrice[]> }) {
  const quotedAt = latestQuotedAt(prices.data);
  const available = prices.data?.filter((p) => p.available) ?? [];

  return (
    <>
      <PriceTicker prices={prices.data ?? []} quotedAt={quotedAt} />

      <section className={styles.section}>
        <div className="wrap">
          <div className={styles.secTitle}>
            <small>TODAY&apos;S PRICE</small>
            <h2>오늘의 귀금속 시세</h2>
            <p>한성금거래소가 고시하는 매입·판매 시세입니다. (단위: 3.75g / 1돈 기준)</p>
          </div>

          <div className={styles.card}>
            <h3>
              고시 시세표
              <span className={styles.date}>{quotedAt ? `${quotedAt} 고시` : '고시 대기'}</span>
            </h3>

            {prices.data ? (
              <MarketPriceTable prices={prices.data} />
            ) : (
              <p className={styles.cardNotice}>
                시세 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
              </p>
            )}
          </div>

          {available.length === 0 && prices.data && (
            <p className={styles.warn}>
              현재 고시된 시세가 없습니다. 시세문의 후 주문해 주세요.
            </p>
          )}

          {/* 1g 기준표 — 시안에는 없지만 상품 가격이 g 단위로 계산되므로 근거를 함께 보인다 */}
          {available.length > 0 && (
            <div className={styles.gramCard}>
              <h3>1g 기준 시세</h3>
              <ul className={styles.gramList}>
                {available
                  .filter((p) => p.priceSide === 'SELL')
                  .map((p) => (
                    <li key={`${p.metalType}-${p.priceSide}`}>
                      <span>{p.metalName}</span>
                      <strong className="tabular">{formatPricePerGram(p.pricePerGram)}</strong>
                    </li>
                  ))}
              </ul>
              <p className={styles.gramNote}>
                상품 판매가는 이 시세에 중량·공임을 반영해 산출됩니다.
              </p>
            </div>
          )}

          <div className={styles.quoteNotes}>
            <span>시세는 당사 기준이며, 시장 상황에 따라 변동될 수 있습니다.</span>
            <span>1돈은 3.75g 입니다.</span>
            <span>부가세 정책 확정 전이며, 현재 표시가는 부가세가 포함되지 않은 금액입니다.</span>
          </div>
        </div>
      </section>

      <section className={styles.guideSec}>
        <div className="wrap">
          <div className={styles.secTitle}>
            <small>GUIDE</small>
            <h2>시세 안내</h2>
          </div>
          <div className={styles.guideGrid}>
            <div className={styles.guide}>
              <h4>
                <span className={styles.n}>1</span>내가 살 때 / 내가 팔 때
              </h4>
              <p>
                &ldquo;내가 살 때&rdquo;는 고객이 구매하는 가격, &ldquo;내가 팔 때&rdquo;는 회사가
                매입하는 가격입니다.
              </p>
            </div>
            <div className={styles.guide}>
              <h4>
                <span className={styles.n}>2</span>가격은 주문 시점에 확정
              </h4>
              <p>
                상품 판매가는 시세에 중량·공임을 반영해 <strong>주문 시점에 서버가 계산</strong>
                합니다. 화면 금액과 결제 금액이 다를 수 없습니다.
              </p>
            </div>
            <div className={styles.guide}>
              <h4>
                <span className={styles.n}>3</span>시세문의 표시
              </h4>
              <p>
                시세가 확인되지 않거나 오래된 경우 금액 대신{' '}
                <strong>&ldquo;시세문의&rdquo;</strong>가 표시되고 주문이 제한됩니다.
              </p>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}

function latestQuotedAt(prices: MarketPrice[] | null): string | null {
  if (!prices) return null;
  const times = prices
    .filter((p) => p.available && p.effectiveFrom)
    .map((p) => p.effectiveFrom as string)
    .sort();
  return times.length > 0 ? formatDateTime(times[times.length - 1]) : null;
}
