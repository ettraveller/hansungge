import { priceApi } from '@/api/prices';
import { productApi } from '@/api/products';
import { load } from '@/api/server';
import type { CategoryProducts } from '@/components/home/ProductTabs';
import type { Category } from '@/types/product';
import { HomeView } from '@/views/HomeView';

/** 제품 소개 탭에서 카테고리당 보여줄 개수. 레퍼런스는 12~15개다. */
const PER_CATEGORY = 8;

/**
 * 요청 시점 렌더링을 명시한다.
 *
 * 이 페이지의 내용은 <b>시세</b>다. 빌드 시점에 굳히면 그 순간의 가격이 화면에 박히고,
 * 시세가 바뀌어도 재배포 전까지 옛 값이 나간다. no-store fetch 때문에 Next.js 가 어차피
 * 동적으로 판정하지만, 의도를 코드로 남겨 두면 나중에 캐시를 붙이는 사람이 이 사실을 본다.
 */
export const dynamic = 'force-dynamic';

/**
 * 메인 (`/`).
 *
 * app/ 아래 page.tsx 는 <b>데이터 로드 + 라우팅 진입점</b>이고, 화면은 views/ 에 둔다.
 * 서버 컴포넌트라 시세·상품이 초기 HTML 에 실려 나간다 (SEO).
 *
 * (Next.js App Router 에서 src/pages 는 Pages Router 예약 디렉터리이므로
 *  화면 디렉터리 이름으로 쓸 수 없다. 그래서 views/ 를 쓴다.)
 */
export default async function HomePage() {
  // 시세와 카테고리를 병렬로 부른다. 순차로 부르면 느린 쪽이 빠른 쪽을 기다린다.
  const [prices, categories] = await Promise.all([
    load(() => priceApi.current(), '메인 시세'),
    load(() => productApi.categories(), '메인 카테고리'),
  ]);

  const groups = await loadProductGroups(categories.data);

  return <HomeView prices={prices} groups={groups} />;
}

/**
 * 최상위 카테고리별 상품 묶음.
 *
 * <p>카테고리 수만큼 요청이 나가므로 <b>반드시 병렬</b>이다. 순차로 돌면 카테고리가
 * 6개일 때 메인 응답이 6배 느려진다.
 *
 * <p>백엔드가 null 필드를 생략하므로({@code non_null}) 최상위 판정은 느슨한 비교여야
 * {@code undefined} 도 잡힌다.
 */
async function loadProductGroups(categories: Category[] | null): Promise<CategoryProducts[]> {
  if (!categories) return [];

  const roots = categories
    .filter((c) => c.parentId == null)
    .sort((a, b) => a.sort - b.sort || a.id - b.id);

  return Promise.all(
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
  );
}
