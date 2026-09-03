import type { Metadata } from 'next';
import { productApi } from '@/api/products';
import { load } from '@/api/server';
import { ProductListView } from '@/views/ProductListView';

export const metadata: Metadata = {
  title: '상품',
  description: '한성금거래소 골드바·순금 주얼리. 고시 시세 기준으로 계산된 판매가를 확인하세요.',
};

/**
 * 요청 시점 렌더링을 명시한다.
 *
 * 이 페이지의 내용은 <b>시세</b>다. 빌드 시점에 굳히면 그 순간의 가격이 화면에 박히고,
 * 시세가 바뀌어도 재배포 전까지 옛 값이 나간다. no-store fetch 때문에 Next.js 가 어차피
 * 동적으로 판정하지만, 의도를 코드로 남겨 두면 나중에 캐시를 붙이는 사람이 이 사실을 본다.
 */
export const dynamic = 'force-dynamic';

/**
 * 상품 목록 (`/shop`).
 *
 * 카테고리는 쿼리 파라미터로 받는다. 링크로 이동하므로 각 카테고리에 고유 URL 이 생기고,
 * 그래야 검색엔진이 카테고리별로 색인한다.
 *
 * Next.js 16 에서 {@code searchParams} 는 Promise 다.
 */
export default async function ShopPage({
  searchParams,
}: {
  searchParams: Promise<{ categoryId?: string; keyword?: string; page?: string }>;
}) {
  const params = await searchParams;
  const categoryId = toPositiveInt(params.categoryId);
  const page = toPositiveInt(params.page);

  const [categories, products] = await Promise.all([
    load(() => productApi.categories(), '상품목록 카테고리'),
    load(
      () =>
        productApi.list({
          categoryId: categoryId ?? undefined,
          keyword: params.keyword,
          page: page == null ? undefined : page - 1, // 화면은 1-base, API 는 0-base
          size: 20,
        }),
      '상품목록',
    ),
  ]);

  return (
    <ProductListView
      categories={categories}
      products={products}
      selectedCategoryId={categoryId}
    />
  );
}

/** 조작된 쿼리로 API 를 부르지 않도록 숫자만 통과시킨다. */
function toPositiveInt(value: string | undefined): number | null {
  if (!value) return null;
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
}
