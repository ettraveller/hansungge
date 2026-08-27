import Link from 'next/link';
import type { Loaded } from '@/api/server';
import { BoardList } from '@/components/home/BoardList';
import { HeroSlider } from '@/components/home/HeroSlider';
import { ProductTabs, type CategoryProducts } from '@/components/home/ProductTabs';
import { StoreLocator } from '@/components/home/StoreLocator';
import { MarketPriceTable } from '@/components/price/MarketPriceTable';
import { PriceTicker } from '@/components/price/PriceTicker';
import type { MarketPrice } from '@/types/price';
import { formatDateTime } from '@/utils/format';
import {
  HERO_SLIDES,
  MID_BANNER,
  NEWS,
  NOTICES,
  REGIONS,
} from './home-content';
import styles from './HomeView.module.css';

interface Props {
  prices: Loaded<MarketPrice[]>;
  groups: CategoryProducts[];
}

/**
 * 메인 화면 — 레퍼런스(kaggold.com)의 섹션 순서를 따른다.
 *
 * <pre>
 * 배너 슬라이드
 * 시세 스트립 (다크 티커)
 * 오늘의 시세 (항목 / 내가 살 때·변동률 / 내가 팔 때·변동률, 1돈 기준)
 * 중간 띠배너
 * 지점안내 (지역별 아코디언)
 * 제품 소개 (카테고리 탭 + 그리드)
 * 뉴스센터
 * 공지사항
 * </pre>
 *
 * <p>서버 컴포넌트다. 시세·상품·지점 주소가 초기 HTML 에 실려 나간다 — 이 프로젝트가
 * Next.js 를 고른 첫 번째 이유가 SEO 다.
 *
 * <p>백엔드가 내려가도 페이지는 뜬다. 실패한 영역만 안내 문구로 바뀐다.
 */
export function HomeView({ prices, groups }: Props) {
  const quotedAt = latestQuotedAt(prices.data);

  return (
    <>
      <HeroSlider slides={HERO_SLIDES} />

      <PriceTicker prices={prices.data ?? []} quotedAt={quotedAt} />

      {/* ---------- 오늘의 시세 ---------- */}
      <section className={styles.quoteSec} id="quote">
        <div className="wrap">
          <div className={styles.secTitle}>
            <small>TODAY&apos;S PRICE</small>
            <h2>오늘의 시세</h2>
            <p>매일 고시 시세를 투명하게 공시합니다. (단위: 3.75g / 1돈 기준)</p>
          </div>

          <div className={styles.card}>
            <h3>
              고시 시세표
              <span className={styles.date}>{quotedAt ? `${quotedAt} 고시` : '고시 대기'}</span>
            </h3>

            {prices.data ? (
              <MarketPriceTable prices={prices.data} />
            ) : (
              <p className={styles.cardNotice}>시세 정보를 불러올 수 없습니다.</p>
            )}

            <div className={styles.quoteNotes}>
              <span>시세는 당사 기준이며, 시장 상황에 따라 변동될 수 있습니다.</span>
              <span>최종 결제 금액은 주문 시점에 서버가 다시 계산합니다.</span>
              <span>부가세 정책 확정 전이며, 현재 표시가는 부가세가 포함되지 않은 금액입니다.</span>
            </div>
          </div>

          <div className={styles.quoteMore}>
            <Link href="/price" className={styles.moreBtn}>
              시세 상세보기
              <span className={styles.arr} aria-hidden="true">
                ›
              </span>
            </Link>
          </div>
        </div>
      </section>

      {/* ---------- 중간 띠배너 ---------- */}
      <section className={styles.midBanner}>
        <div className={`wrap ${styles.midInner}`}>
          <div>
            <small>{MID_BANNER.eyebrow}</small>
            <h2>{MID_BANNER.title}</h2>
            <p>{MID_BANNER.desc}</p>
          </div>
          <Link href={MID_BANNER.href} className={styles.midCta}>
            {MID_BANNER.cta}
            <span className={styles.arr} aria-hidden="true">
              ›
            </span>
          </Link>
        </div>
      </section>

      {/* ---------- 지점안내 ---------- */}
      <section className={styles.section}>
        <div className="wrap">
          <div className={styles.secTitle}>
            <small>STORE</small>
            <h2>전국 한성금거래소 지점 안내</h2>
            <p>가까운 지점에서 실물 확인과 매입 상담을 받으실 수 있습니다.</p>
          </div>
          <StoreLocator regions={REGIONS} />
        </div>
      </section>

      {/* ---------- 제품 소개 ---------- */}
      <section className={styles.quoteSec} id="products">
        <div className="wrap">
          <div className={styles.secTitle}>
            <small>PRODUCTS</small>
            <h2>제품 소개</h2>
            <p>한성금거래소가 자신 있게 추천하는 상품입니다.</p>
          </div>
          <ProductTabs groups={groups} />
        </div>
      </section>

      {/* ---------- 뉴스센터 · 공지사항 ---------- */}
      <section className={styles.section}>
        <div className={`wrap ${styles.boards}`}>
          <BoardList title="뉴스센터" moreHref="/news" basePath="/news" posts={NEWS} />
          <BoardList title="공지사항" moreHref="/notice" basePath="/notice" posts={NOTICES} />
        </div>
      </section>
    </>
  );
}

/** 표에 실린 시세 중 가장 최근 기준시각. 없으면 null. */
function latestQuotedAt(prices: MarketPrice[] | null): string | null {
  if (!prices) return null;
  const times = prices
    .filter((p) => p.available && p.effectiveFrom)
    .map((p) => p.effectiveFrom as string)
    .sort();
  return times.length > 0 ? formatDateTime(times[times.length - 1]) : null;
}
