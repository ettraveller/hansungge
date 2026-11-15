import Link from 'next/link';
import type { Loaded } from '@/api/server';
import { ProductCard } from '@/components/product/ProductCard';
import { Reveal } from '@/components/ui/Reveal';
import { SectionHead } from '@/components/ui/SectionHead';
import type { PageResponse } from '@/types/api';
import type { Category, ProductSummary } from '@/types/product';
import styles from './ProductListView.module.css';

/** 넓은 화면의 한 줄 카드 수 — {@code .prodGrid} 의 열 수와 같아야 한다. */
const CARDS_PER_ROW = 4;

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
  const all = categories.data ?? [];
  const current = all.find((c) => c.id === selectedCategoryId) ?? null;
  const currentName = current?.name ?? null;

  /**
   * 1단 메뉴. 자식은 여기 세우지 않는다 — 평평하게 늘어놓으면 「골드바」와
   * 「선물 골드바」가 같은 줄에 나란히 서서, 하나가 다른 하나 안에 든 것이 보이지 않는다.
   */
  const roots = all.filter((c) => c.parentId == null);

  /**
   * 2단 메뉴. 지금 보고 있는 분류의 형제들이다.
   *
   * <p>부모를 골랐으면 그 자식들을, 자식을 골랐으면 같은 부모의 자식들을 세운다.
   * 자식을 고른 뒤에 2단이 사라지면 옆 분류로 건너갈 길이 없어져, 뒤로 가기만 남는다.
   */
  const branchId = current?.parentId ?? selectedCategoryId;
  const children = branchId == null ? [] : all.filter((c) => c.parentId === branchId);

  return (
    <section className={styles.section}>
      <div className="wrap">
        <SectionHead
          as="h1"
          title={currentName ?? '전체 상품'}
          desc="가격은 현재 고시 시세 기준으로 계산되어 표시됩니다"
        />

        {roots.length > 0 && (
          <nav className={styles.tabs} aria-label="카테고리">
            <Link href="/shop" className={selectedCategoryId === null ? styles.tabOn : styles.tab}>
              전체
            </Link>
            {roots.map((category) => (
              <Link
                key={category.id}
                href={`/shop?categoryId=${category.id}`}
                // 자식을 보고 있어도 부모가 눌린 채로 둔다. 안 그러면 「골드바」 안에
                // 있는데 1단에는 아무것도 눌려 있지 않아, 어디에 와 있는지 사라진다.
                className={branchId === category.id ? styles.tabOn : styles.tab}
              >
                {category.name}
              </Link>
            ))}
          </nav>
        )}

        {children.length > 0 && (
          <nav className={styles.subTabs} aria-label={`${currentName ?? ''} 하위 분류`}>
            <Link
              href={`/shop?categoryId=${branchId}`}
              className={selectedCategoryId === branchId ? styles.subTabOn : styles.subTab}
            >
              전체
            </Link>
            {children.map((category) => (
              <Link
                key={category.id}
                href={`/shop?categoryId=${category.id}`}
                className={selectedCategoryId === category.id ? styles.subTabOn : styles.subTab}
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
            {/* 카드가 한 줄씩 차례로 올라온다. 지연을 한 줄까지만 쌓는 이유는
                메인과 같다 — 끝까지 쌓으면 뒤쪽 카드가 한참 뒤에 도착해
                "느리게 뜨는 사이트"가 된다. 목록은 메인보다 카드가 훨씬 많아
                그 차이가 더 크게 난다. */}
            <div className={styles.prodGrid}>
              {items.map((product, index) => (
                <Reveal key={product.id} rise="sm" delay={(index % CARDS_PER_ROW) * 70}>
                  <ProductCard product={product} />
                </Reveal>
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
