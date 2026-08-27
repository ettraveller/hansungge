'use client';

import Link from 'next/link';
import { useState } from 'react';
import { ProductCard } from '@/components/product/ProductCard';
import type { ProductSummary } from '@/types/product';
import styles from './ProductTabs.module.css';

export interface CategoryProducts {
  categoryId: number;
  name: string;
  products: ProductSummary[];
}

/**
 * 카테고리 탭 상품 목록 (레퍼런스의 제품 소개 섹션).
 *
 * <h2>모든 탭의 상품이 DOM 에 있다</h2>
 * 활성 탭만 렌더링하면 나머지 상품명·가격이 <b>초기 HTML 에서 빠진다.</b> 상품명은
 * 이 사이트에서 검색 유입이 걸리는 핵심 텍스트라, 숨기더라도 문서에는 남긴다.
 * 서버가 이미 전 카테고리 상품을 실어 보내므로 탭 전환에 추가 요청이 없다.
 *
 * <p>탭을 링크가 아니라 버튼으로 둔 이유: 카테고리별 <b>고유 URL 은 이미
 * {@code /shop?categoryId=} 가 갖고 있다.</b> 메인의 이 섹션은 둘러보기용이라
 * 페이지 이동 없이 전환되는 편이 낫다.
 */
export function ProductTabs({ groups }: { groups: CategoryProducts[] }) {
  const withProducts = groups.filter((g) => g.products.length > 0);
  const [activeId, setActiveId] = useState(withProducts[0]?.categoryId ?? 0);

  if (withProducts.length === 0) {
    return <p className={styles.empty}>등록된 상품이 아직 없습니다.</p>;
  }

  return (
    <>
      <div className={styles.tabs} role="tablist" aria-label="상품 카테고리">
        {withProducts.map((group) => (
          <button
            key={group.categoryId}
            type="button"
            role="tab"
            aria-selected={group.categoryId === activeId}
            className={group.categoryId === activeId ? styles.tabOn : styles.tab}
            onClick={() => setActiveId(group.categoryId)}
          >
            {group.name}
          </button>
        ))}
      </div>

      {withProducts.map((group) => (
        <div
          key={group.categoryId}
          role="tabpanel"
          aria-label={group.name}
          hidden={group.categoryId !== activeId}
        >
          <div className={styles.grid}>
            {group.products.map((product, index) => (
              <ProductCard
                key={product.id}
                product={product}
                flag={index < 2 ? 'new' : index < 4 ? 'best' : undefined}
              />
            ))}
          </div>

          <div className={styles.moreWrap}>
            <Link href={`/shop?categoryId=${group.categoryId}`} className={styles.moreBtn}>
              {group.name} 전체보기
              <span className={styles.arr} aria-hidden="true">
                ›
              </span>
            </Link>
          </div>
        </div>
      ))}
    </>
  );
}
