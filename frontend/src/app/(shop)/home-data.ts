import { noticeApi } from '@/api/board';
import { priceApi } from '@/api/prices';
import { productApi } from '@/api/products';
import { load, type Loaded } from '@/api/server';
import type { HeroBanner } from '@/api/banners';
import { bannerApi } from '@/api/banners';
import { ALL_PRODUCTS_TAB, type CategoryProducts } from '@/components/home/ProductTabs';
import type { PageResponse } from '@/types/api';
import type { NoticeSummary } from '@/types/board';
import type { Category } from '@/types/product';
import { METAL_ORDER, type MarketPrice, type MetalType, type PriceHistoryPoint } from '@/types/price';

/** 제품 소개 탭에서 카테고리당 보여줄 개수. 레퍼런스는 12~15개다. */
const PER_CATEGORY = 8;

/** 시세 보드의 추이 그래프 기간. 시안 문구("최근 30일 추이")와 같아야 한다. */
const HISTORY_DAYS = 30;

/** 메인 공지 위젯에 보여줄 건수. BoardList 가 4줄짜리 요약 위젯이다. */
const HOME_NOTICES = 4;

export interface HomeData {
  prices: Loaded<MarketPrice[]>;
  histories: Partial<Record<MetalType, PriceHistoryPoint[]>>;
  groups: CategoryProducts[];
  notices: Loaded<PageResponse<NoticeSummary>>;
  banners: HeroBanner[];
  /**
   * GNB 를 만드는 카테고리.
   *
   * <p>메인은 쓰지 않는다 — 머리띠를 레이아웃이 그리기 때문이다. 배너 미리보기는
   * 레이아웃 밖에서 머리띠를 직접 그리므로 이 값이 필요하다. 상품 묶음을 만들 때
   * 이미 받아 둔 것이라 요청이 늘지 않는다.
   */
  categories: Category[];
}

/**
 * 메인 화면이 필요한 것 전부.
 *
 * <p>메인(`/`)과 배너 미리보기(`/banner-preview`)가 같은 것을 쓴다. 미리보기는
 * <b>배너만 바꿔 끼운 메인</b>이어야 하기 때문이다 — 배너 조각만 따로 그리면
 * 시세표·상품 아래에서 어떻게 보이는지는 저장한 뒤에야 알게 된다.
 *
 * <p>전부 병렬이다. 순차로 부르면 느린 쪽이 빠른 쪽을 기다린다.
 */
export async function loadHomeData(): Promise<HomeData> {
  const [prices, categories, histories, notices, banners] = await Promise.all([
    load(() => priceApi.current(), '메인 시세'),
    load(() => productApi.categories(), '메인 카테고리'),
    loadHistories(),
    load(() => noticeApi.list({ size: HOME_NOTICES }), '메인 공지'),
    // 배너는 없을 수 있다. 실패해도 메인을 멈추지 않는다 — 화면이 기본 배너로 돌아간다.
    load(() => bannerApi.list(), '메인 배너'),
  ]);

  return {
    prices,
    histories,
    notices,
    groups: await loadProductGroups(categories.data),
    banners: banners.data ?? [],
    categories: categories.data ?? [],
  };
}

/**
 * 금속별 30일 추이.
 *
 * <p>시세 보드는 마우스를 올린 금속으로 그래프가 <b>즉시</b> 바뀐다. 그러려면 5종을
 * 미리 다 갖고 있어야 한다 — 호버할 때마다 요청을 보내면 그래프가 늦게 뜨고, 마우스를
 * 훑는 것만으로 요청이 쏟아진다.
 *
 * <p>한 금속이 실패해도 나머지는 그린다. 그래프는 부가 정보라, 여기서 예외를 던지면
 * 정작 중요한 시세표까지 사라진다.
 */
async function loadHistories(): Promise<Partial<Record<MetalType, PriceHistoryPoint[]>>> {
  const entries = await Promise.all(
    METAL_ORDER.map(async (metal) => {
      const result = await load(() => priceApi.history(metal, HISTORY_DAYS), `메인 추이 (${metal})`);
      return [metal, result.data ?? []] as const;
    }),
  );
  return Object.fromEntries(entries);
}

/**
 * 「전체상품」 + 최상위 카테고리별 상품 묶음.
 *
 * <p>카테고리 수만큼 요청이 나가므로 <b>반드시 병렬</b>이다. 순차로 돌면 카테고리가
 * 6개일 때 메인 응답이 6배 느려진다. 전체 조회도 그 병렬에 함께 넣는다 — 앞에
 * 따로 await 하면 그 한 번이 나머지 전부를 기다리게 한다.
 *
 * <p>백엔드가 null 필드를 생략하므로({@code non_null}) 최상위 판정은 느슨한 비교여야
 * {@code undefined} 도 잡힌다.
 *
 * <p>2026-11-05: 맨 앞에 「전체상품」을 둔다. 카테고리 목록을 못 불러와도 이 묶음은
 * 나가므로, 전에는 통째로 비던 자리에 상품이 남는다 ({@link ALL_PRODUCTS_TAB}).
 */
async function loadProductGroups(categories: Category[] | null): Promise<CategoryProducts[]> {
  const roots = (categories ?? [])
    .filter((c) => c.parentId == null)
    .sort((a, b) => a.sort - b.sort || a.id - b.id);

  const [all, byCategory] = await Promise.all([
    load(() => productApi.list({ size: PER_CATEGORY }), '메인 상품 (전체)'),
    Promise.all(
      roots.map(async (root) => {
        const page = await load(
          () => productApi.list({ categoryId: root.id, size: PER_CATEGORY }),
          `메인 상품 (${root.name})`,
        );
        return {
          categoryId: root.id,
          name: root.name,
          products: page.data?.content ?? [],
        };
      }),
    ),
  ]);

  return [
    {
      categoryId: ALL_PRODUCTS_TAB.id,
      name: ALL_PRODUCTS_TAB.name,
      products: all.data?.content ?? [],
    },
    ...byCategory,
  ];
}
