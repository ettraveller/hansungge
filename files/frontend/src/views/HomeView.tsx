import Link from 'next/link';
import type { Loaded } from '@/api/server';
import { BoardList } from '@/components/home/BoardList';
import { HeroSlider } from '@/components/home/HeroSlider';
import { ProductTabs, type CategoryProducts } from '@/components/home/ProductTabs';
import { StoreLocator } from '@/components/home/StoreLocator';
import { TrustStrip } from '@/components/home/TrustStrip';
import { MarketPriceBoard } from '@/components/price/MarketPriceBoard';
import { toMetalRows } from '@/components/price/metalRows';
import type { MarketPrice, MetalType, PriceHistoryPoint } from '@/types/price';
import { formatDateTime } from '@/utils/format';
import { HERO_SLIDES, NEWS, NOTICES, REGIONS } from './home-content';
import styles from './HomeView.module.css';

interface Props {
  prices: Loaded<MarketPrice[]>;
  histories: Partial<Record<MetalType, PriceHistoryPoint[]>>;
  groups: CategoryProducts[];
}

/**
 * 메인 화면.
 *
 * <pre>
 * 히어로 배너
 * 오늘의 고시 시세  ★ 이 화면의 주인공
 * 신뢰 스트립
 * 시세 연동 상품
 * 전국 지점 안내
 * 뉴스센터 · 공지사항
 * </pre>
 *
 * <h2>시세가 맨 앞인 이유</h2>
 * 이 시스템의 강점은 시세가 상품 가격을 실시간으로 결정한다는 것이다. 그 구조가 화면에
 * 드러나지 않으면 다른 쇼핑몰과 구분되지 않는다. 배너 아래 첫 섹션이 시세표이고,
 * 이 페이지에서 <b>가장 큰 글자가 시세</b>다.
 *
 * <h2>서버 컴포넌트다</h2>
 * 시세·상품·지점 주소가 초기 HTML 에 실려 나간다 — 이 프로젝트가 Next.js 를 고른
 * 첫 번째 이유가 SEO 다. 호버로 그래프를 바꾸는 시세 보드만 클라이언트 컴포넌트이고,
 * 그 값도 전부 여기서 props 로 넘긴다.
 *
 * <h2>백엔드가 내려가도 페이지는 뜬다</h2>
 * 실패한 영역만 안내 문구로 바뀐다.
 */
export function HomeView({ prices, histories, groups }: Props) {
  const rows = toMetalRows(prices.data ?? [], histories);
  const quotedAt = latestQuotedAt(prices.data);

  return (
    <>
      <HeroSlider slides={HERO_SLIDES} />

      {/* ---------- 오늘의 고시 시세 ---------- */}
      <div className={styles.priceSection} id="quote">
        {prices.data ? (
          <MarketPriceBoard rows={rows} quotedAt={quotedAt} />
        ) : (
          <p className={styles.loadError}>
            시세 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        )}
      </div>

      <TrustStrip />

      {/* ---------- 시세 연동 상품 ---------- */}
      <section className={styles.section} id="products">
        <div className="wrap">
          <SectionHead
            title="시세 연동 상품"
            desc="시세가 바뀌면 가격도 함께 바뀝니다"
            moreHref="/shop"
            moreLabel="전체 보기"
          />
          <ProductTabs groups={groups} />
        </div>
      </section>

      {/* ---------- 전국 지점 안내 ---------- */}
      <section className={styles.sectionTinted}>
        <div className="wrap">
          <SectionHead
            title="전국 지점 안내"
            desc="가까운 지점에서 실물 확인과 매입 상담을 받으실 수 있습니다"
            moreHref="/store"
            moreLabel="전체 지점 보기"
          />
          <StoreLocator regions={REGIONS} />
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

/**
 * 섹션 머리 — 좌측정렬 제목 + 설명, 우측 더보기.
 *
 * <p>가운데정렬 3줄(작은영문/제목/설명)을 섹션마다 반복하지 않는다. 같은 형태가 네 번
 * 이어지면 스크롤에 리듬이 없어지고, 어디까지 읽었는지 알기 어려워진다.
 */
function SectionHead({
  title,
  desc,
  moreHref,
  moreLabel,
}: {
  title: string;
  desc: string;
  moreHref: string;
  moreLabel: string;
}) {
  return (
    <div className={styles.secHead}>
      <h2 className={styles.secTitle}>{title}</h2>
      <p className={styles.secDesc}>{desc}</p>
      <Link href={moreHref} className={styles.more}>
        {moreLabel}
      </Link>
    </div>
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
