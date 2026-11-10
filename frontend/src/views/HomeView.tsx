import type { Loaded } from '@/api/server';
import { BoardList, type BoardPost } from '@/components/home/BoardList';
import { HeroSlider } from '@/components/home/HeroSlider';
import type { HeroBanner } from '@/api/banners';
import { mediaUrl } from '@/utils/media';
import { ProductTabs, type CategoryProducts } from '@/components/home/ProductTabs';
import { StoreLocator } from '@/components/home/StoreLocator';
import { MarketPriceBoard } from '@/components/price/MarketPriceBoard';
import { toMetalRows } from '@/components/price/metalRows';
import { Reveal } from '@/components/ui/Reveal';
import { SectionHead } from '@/components/ui/SectionHead';
import { REGIONS } from '@/data/branches';
import type { PageResponse } from '@/types/api';
import type { NoticeSummary } from '@/types/board';
import type { MarketPrice, MetalType, PriceHistoryPoint } from '@/types/price';
import { formatBoardDate, formatDateTime } from '@/utils/format';
import { HERO_SLIDES } from './home-content';
import styles from './HomeView.module.css';

interface Props {
  prices: Loaded<MarketPrice[]>;
  histories: Partial<Record<MetalType, PriceHistoryPoint[]>>;
  groups: CategoryProducts[];
  notices: Loaded<PageResponse<NoticeSummary>>;
  /** 관리자가 올린 메인 배너. 비어 있으면 화면이 기본 배너를 쓴다 */
  banners: HeroBanner[];
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
export function HomeView({ prices, histories, groups, notices, banners }: Props) {
  const rows = toMetalRows(prices.data ?? [], histories);
  const quotedAt = latestQuotedAt(prices.data);

  return (
    <>
      {/* 관리자가 올린 배너가 있으면 그것만 나간다. 없을 때만 기본 배너로 돌아간다 —
          비워 두면 메인 맨 위가 통째로 사라진다. */}
      <HeroSlider
        slides={HERO_SLIDES}
        banners={banners.map((b) => ({
          id: b.id,
          image: mediaUrl(b.imageKey) ?? '',
          mobileImage: mediaUrl(b.mobileImageKey),
          href: b.linkHref,
          alt: b.altText,
        }))}
      />

      {/*
        ---------- 오늘의 고시 시세 ----------

        ⚠️ 여기에는 등장 효과를 걸지 않는다.

        이 화면의 주인공이고, 히어로 바로 아래라 대부분 스크롤하기 전에 이미
        보인다. 그리고 <b>금액이 떠오르며 나타나면 값이 방금 바뀐 것처럼</b>
        읽힌다 — 시세를 보러 온 사람에게 그 한 순간의 착각이 제일 나쁘다.
        돈이 적힌 자리는 처음부터 거기 있어야 한다.
      */}
      <div className={styles.priceSection} id="quote">
        {prices.data ? (
          <MarketPriceBoard rows={rows} quotedAt={quotedAt} />
        ) : (
          <p className={styles.loadError}>
            시세 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        )}
      </div>

      {/* ---------- 시세 연동 상품 ---------- */}
      <section className={styles.section} id="products">
        <div className="wrap">
          <SectionHead
            title="시세 연동 상품"
            desc="시세가 바뀌면 가격도 함께 바뀝니다"
            moreHref="/shop"
            moreLabel="전체 보기"
          />
          {/* 여기는 Reveal 로 싸지 않는다 — 카드가 저마다 차례로 올라온다
              (ProductTabs). 겹쳐 싸면 같은 움직임이 두 번 돈다. */}
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
          <Reveal rise="lg" delay={120}>
            <StoreLocator regions={REGIONS} />
          </Reveal>
        </div>
      </section>

      {/* ---------- 공지사항 ---------- */}
      <section className={styles.section}>
        <div className={`wrap ${styles.boards}`}>
          <Reveal>
            <BoardList
              title="공지사항"
              moreHref="/notice"
              basePath="/notice"
              posts={toBoardPosts(notices.data?.content)}
            />
          </Reveal>
        </div>
      </section>
    </>
  );
}

/** 최근 며칠 안에 올라온 글에 NEW 를 붙일지. */
const FRESH_DAYS = 7;

/**
 * 공지 응답 → 메인 위젯이 쓰는 모양.
 *
 * <p>{@code BoardPost.date} 는 <b>이미 포맷된 문자열</b>이다 — 위젯은 표시만 하고
 * 날짜를 해석하지 않는다. 조회 실패(백엔드 다운)면 빈 배열이 가고, 위젯이 "등록된 글이
 * 없습니다"를 그린다. 메인 전체가 죽는 것보다 낫다.
 */
function toBoardPosts(notices: NoticeSummary[] | undefined): BoardPost[] {
  if (!notices) return [];

  const freshLine = Date.now() - FRESH_DAYS * 24 * 60 * 60 * 1000;

  return notices.map((notice) => ({
    id: notice.id,
    title: notice.title,
    date: formatBoardDate(notice.createdAt),
    fresh: new Date(notice.createdAt).getTime() >= freshLine,
  }));
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
