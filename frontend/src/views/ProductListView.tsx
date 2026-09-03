import Link from 'next/link';
import type { Loaded } from '@/api/server';
import { ProductCard } from '@/components/product/ProductCard';
import { SectionHead } from '@/components/ui/SectionHead';
import type { PageResponse } from '@/types/api';
import type { Category, ProductSummary } from '@/types/product';
import styles from './ProductListView.module.css';

interface Props {
  categories: Loaded<Category[]>;
  products: Loaded<PageResponse<ProductSummary>>;
  /** 현재 선택된 카테고리. 없으면 전체 */
  selectedCategoryId: number | null;
}

/**
 * 상품 목록 (`/shop`) — 시안의 상품 섹션(`.tabs` + `.prod-grid`)을 그대로 쓴다.
 *
 * <p>카테고리 필터를 시안의 탭 모양으로 만들되 <b>버튼이 아니라 링크</b>다.
 * 클라이언트 상태로 두면 각 카테고리에 고유 URL 이 없어져 검색엔진이 색인할 페이지가
 * 하나로 줄어든다.
 */
export function ProductListView({ categories, products, selectedCategoryId }: Props) {
  const page = products.data;
  const items = page?.content ?? [];
  const currentName =
    categories.data?.find((c) => c.id === selectedCategoryId)?.name ?? null;

  return (
    <section className={styles.section}>
      <div className="wrap">
        <SectionHead
          as="h1"
          title={currentName ?? '전체 상품'}
          desc="가격은 현재 고시 시세 기준으로 계산되어 표시됩니다"
        />

        {categories.data && categories.data.length > 0 && (
          <nav className={styles.tabs} aria-label="카테고리">
            <Link href="/shop" className={selectedCategoryId === null ? styles.tabOn : styles.tab}>
              전체
            </Link>
            {categories.data.map((category) => (
              <Link
                key={category.id}
                href={`/shop?categoryId=${category.id}`}
                className={selectedCategoryId === category.id ? styles.tabOn : styles.tab}
              >
                {category.name}
              </Link>
            ))}
          </nav>
        )}

        {!page ? (
          <p className={styles.empty}>
            상품 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : items.length === 0 ? (
          <p className={styles.empty}>
            {selectedCategoryId === null
              ? '판매 중인 상품이 아직 없습니다.'
              : '이 카테고리에 판매 중인 상품이 없습니다.'}
          </p>
        ) : (
          <>
            <p className={styles.count}>
              총 <strong className="tabular">{page.totalElements}</strong>개
            </p>
            <div className={styles.prodGrid}>
              {items.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
            {page.totalPages > 1 && (
              <p className={styles.pager}>
                {page.page + 1} / {page.totalPages} 페이지
              </p>
            )}
          </>
        )}
      </div>
    </section>
  );
}
