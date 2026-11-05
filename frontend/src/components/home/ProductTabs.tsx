'use client';

import Link from 'next/link';
import { useState } from 'react';
import { ProductCard } from '@/components/product/ProductCard';
import { Reveal } from '@/components/ui/Reveal';
import type { ProductSummary } from '@/types/product';
import styles from './ProductTabs.module.css';

/**
 * 넓은 화면의 한 줄 카드 수. 등장 지연을 여기까지만 쌓는다.
 *
 * <p>격자가 좁은 화면에서 2~3열로 줄어도 그대로 둔다 — 차례가 조금 어긋날 뿐
 * 어색하지 않고, 화면 폭을 읽어 지연을 다시 계산하면 그 계산이 리사이즈마다 돈다.
 */
const CARDS_PER_ROW = 4;

export interface CategoryProducts {
  categoryId: number;
  name: string;
  products: ProductSummary[];
}

/**
 * 맨 앞에 서는 「전체상품」 탭 (2026-11-05).
 *
 * <p>전에는 첫 카테고리(골드바)가 열린 채로 메인이 떴다. 처음 온 사람에게 그것은
 * <b>"이 가게는 골드바를 판다"</b>로 읽힌다 — 돌선물·주얼리를 보러 온 사람은 탭이
 * 있다는 것을 알아차리기 전에 지나간다. 무엇을 파는지 먼저 보이고 나서 고르게 한다.
 *
 * <p>{@code id} 는 0 이다. 카테고리 식별자가 아니라 <b>카테고리 없음</b>을 뜻한다 —
 * 상품 목록 API 에 {@code categoryId} 를 빼고 부른 결과이고, 「전체보기」도
 * {@code /shop} 으로 간다. 실제 카테고리 id 는 1부터라 겹치지 않는다.
 */
export const ALL_PRODUCTS_TAB = { id: 0, name: '전체상품' } as const;

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
  // 「전체상품」이 맨 앞에 오므로 보통 그것이 열린다. 전체 조회가 실패해 그 묶음이
  // 비면 첫 카테고리로 내려간다 — 빈 화면을 보여주는 것보다 낫다.
  const [activeId, setActiveId] = useState(withProducts[0]?.categoryId ?? ALL_PRODUCTS_TAB.id);

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
          {/*
            카드가 하나씩 차례로 올라온다.

            지연을 한 줄(4장)까지만 쌓는다. 끝까지 쌓으면 12번째 카드가
            0.7초 뒤에 도착해 "느리게 뜨는 사이트"가 된다 — 기다리게 하려고
            넣는 움직임이 아니다. 한 줄만 어긋나도 차례가 충분히 읽힌다.
          */}
          <div className={styles.grid}>
            {group.products.map((product, index) => (
              <Reveal key={product.id} rise="sm" delay={(index % CARDS_PER_ROW) * 70}>
                <ProductCard
                  product={product}
                  flag={index < 2 ? 'new' : index < 4 ? 'best' : undefined}
                />
              </Reveal>
            ))}
          </div>

          <div className={styles.moreWrap}>
            <Link
              href={
                group.categoryId === ALL_PRODUCTS_TAB.id
                  ? '/shop'
                  : `/shop?categoryId=${group.categoryId}`
              }
              className={styles.moreBtn}
            >
              {group.categoryId === ALL_PRODUCTS_TAB.id ? '상품 전체보기' : `${group.name} 전체보기`}
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
