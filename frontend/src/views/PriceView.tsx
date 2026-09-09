import type { Loaded } from '@/api/server';
import { MarketPriceBoard } from '@/components/price/MarketPriceBoard';
import { toMetalRows } from '@/components/price/metalRows';
import { GlobalPriceWidgets } from '@/components/price/GlobalPriceWidgets';
import { PriceHistorySection } from '@/components/price/PriceHistorySection';
import { SectionHead } from '@/components/ui/SectionHead';
import type { MarketPrice, MetalType, PriceHistoryPoint } from '@/types/price';
import { formatDateTime, formatPricePerGram } from '@/utils/format';
import styles from './PriceView.module.css';

interface Props {
  prices: Loaded<MarketPrice[]>;
  /** 시세 보드의 스파크라인용 30일 추이 (5종) */
  histories: Partial<Record<MetalType, PriceHistoryPoint[]>>;
  /** 추이 구역이 보여줄 금속·기간. URL 에서 온다 */
  metal: MetalType;
  days: number;
  expanded: boolean;
  /** 선택한 조건의 살 때 / 팔 때 이력 */
  sell: PriceHistoryPoint[];
  buy: PriceHistoryPoint[];
}

/**
 * 금시세 화면 (`/price`).
 *
 * <p>메인과 <b>같은 시세 보드</b>를 쓴다. 화면마다 시세를 다르게 그리면 어느 쪽이 맞는지
 * 고객이 판단해야 한다 — 같은 값을 같은 모양으로 보여주는 것이 이 도메인에서는 디자인
 * 문제가 아니라 신뢰 문제다.
 *
 * <p>메인에 앵커(`#quote`)가 있는데도 독립 페이지를 두는 이유는 <b>SEO</b> 다.
 * "금시세"는 이 업종에서 검색량이 가장 큰 키워드이고, 앵커는 독립 URL 이 아니라
 * 색인되지 않는다.
 */
export function PriceView({ prices, histories, metal, days, expanded, sell, buy }: Props) {
  const rows = toMetalRows(prices.data ?? [], histories);
  const quotedAt = latestQuotedAt(prices.data);
  const sellable = (prices.data ?? []).filter((p) => p.available && p.priceSide === 'SELL');

  return (
    <>
      <div className={styles.boardSection}>
        {prices.data ? (
          <MarketPriceBoard rows={rows} quotedAt={quotedAt} />
        ) : (
          <p className={styles.loadError}>
            시세 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        )}
      </div>

      {/*
        추이는 시세표 바로 아래다. 읽는 순서가 오늘 → 추세 → 이력이라 질문의 순서와
        같다. 그래프가 실패해도 위의 오늘 시세는 그대로 남는다 — 레퍼런스는 차트가
        죽으면 페이지가 통째로 빈다.
      */}
      {prices.data && (
        <PriceHistorySection
          metal={metal}
          days={days}
          sell={sell}
          buy={buy}
          expanded={expanded}
        />
      )}

      <section className={styles.section}>
        <div className="wrap">
          {sellable.length === 0 && prices.data ? (
            <p className={styles.warn}>
              현재 고시된 시세가 없습니다. 시세문의 후 주문해 주세요.
            </p>
          ) : (
            /* 1g 기준표 — 상품 가격이 g 단위로 계산되므로 그 근거를 함께 보인다.
               시세표(1돈)만 두면 상세페이지의 "210,000원/g × 3.75g" 이 어디서 왔는지
               고객이 되짚을 수 없다. */
            sellable.length > 0 && (
              <>
                <SectionHead
                  title="1g 기준 시세"
                  desc="판매가는 이 시세에 중량과 공임을 반영해 산정됩니다"
                />
                <ul className={styles.gramList}>
                  {sellable.map((p) => (
                    <li key={p.metalType} className={styles.gramItem}>
                      <span className={styles.gramName}>{p.metalName}</span>
                      <strong className={`${styles.gramPrice} tabular`}>
                        {formatPricePerGram(p.pricePerGram)}
                      </strong>
                    </li>
                  ))}
                </ul>
              </>
            )
          )}
        </div>
      </section>

      {/* 국제 시세 — 우리 고시가 아래에 둔다. 순서를 뒤집으면 국제가가 우리
          판매 기준인 것처럼 읽힌다. 실제 거래 기준은 위의 고시 시세다. */}
      <section className={styles.sectionTinted}>
        <div className="wrap">
          <SectionHead
            title="국제 시세"
            desc="런던·뉴욕 시장의 현물 시세입니다. 당사 고시 시세와는 다릅니다"
          />
          <GlobalPriceWidgets />
        </div>
      </section>

      <section className={styles.guideSection}>
        <div className="wrap">
          <SectionHead title="시세 안내" />

          <div className={styles.guides}>
            <Guide
              n="1"
              title="내가 살 때 / 내가 팔 때"
              body={
                <>
                  &ldquo;내가 살 때&rdquo;는 고객이 구매하는 가격, &ldquo;내가 팔 때&rdquo;는
                  회사가 매입하는 가격입니다.
                </>
              }
            />
            <Guide
              n="2"
              title="가격은 주문 시점에 확정"
              body={
                <>
                  판매가는 고시 시세에 중량과 공임을 반영해 산정되며,{' '}
                  <strong>주문하시는 시각의 시세로 확정</strong>됩니다. 화면에 보신 금액과
                  결제 금액은 다르지 않습니다.
                </>
              }
            />
            <Guide
              n="3"
              title="시세문의 표시"
              body={
                <>
                  시세가 확인되지 않은 경우 금액 대신{' '}
                  <strong>&ldquo;시세문의&rdquo;</strong>로 안내드립니다. 확인되지 않은 가격으로
                  거래하지 않기 위한 원칙입니다.
                </>
              }
            />
          </div>

          <p className={styles.notes}>
            <span>· 시세는 당사 기준이며, 시장 상황에 따라 변동될 수 있습니다.</span>
            <span>· 1돈은 3.75g 입니다.</span>
            <span>· 표시 금액은 부가세가 포함되지 않은 금액입니다.</span>
          </p>
        </div>
      </section>
    </>
  );
}

function Guide({ n, title, body }: { n: string; title: string; body: React.ReactNode }) {
  return (
    <div className={styles.guide}>
      <p className={styles.guideTitle}>
        <span className={`${styles.guideNum} tabular`} aria-hidden="true">
          {n}
        </span>
        {title}
      </p>
      <p className={styles.guideBody}>{body}</p>
    </div>
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
