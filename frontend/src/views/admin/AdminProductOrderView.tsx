'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Panel } from '@/components/admin/Panel';
// ⚠️ 고객이 보는 바로 그 카드다. 관리자용으로 비슷하게 다시 그리지 않는다 —
// 그러면 쇼핑몰 쪽이 바뀔 때 이 화면만 낡아서, 정작 맞춰 보려던 "손님이 보는 모양"이
// 아니게 된다. ProductPreviewModal 이 상세 화면에 대해 하는 것과 같은 방식이다.
import { ProductCard } from '@/components/product/ProductCard';
import { useAdminCategories } from '@/hooks/admin/useCategories';
import { useReorderProducts } from '@/hooks/admin/useProducts';
import { productApi } from '@/api/products';
import type { ProductSummary } from '@/types/product';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/** 한 번에 끌어다 놓을 수 있는 최대 개수. 넘으면 저장은 되지만 화면이 길어진다. */
const MAX_PRODUCTS = 500;

/**
 * 진열 순서 — 손님이 보는 목록을 그대로 띄워 놓고 끌어다 놓는다.
 *
 * <h2>관리자 목록이 아니라 공개 목록을 쓴다</h2>
 * 여기서 맞추려는 것은 <b>손님 화면의 차례</b>다. 관리자 목록에는 가격도 뱃지도 없고
 * 숨김 상품까지 섞여 있어, 그것으로 그리면 줄바꿈 자리도 카드 모양도 달라진다.
 * 그래서 공개 목록(/api/v1/products)을 그대로 받아 공개 카드로 그린다.
 *
 * <p>대신 <b>숨김 상품은 보이지 않는다.</b> 진열은 내걸린 것들의 차례라 그것이 맞고,
 * 보이지 않는 상품의 자리는 서버가 그대로 지켜 준다 — 보낸 상품들끼리만 자리를 바꾼다.
 *
 * <h2>폭을 쇼핑몰에 맞춘다</h2>
 * 관리자 화면이 훨씬 넓어서 그대로 두면 같은 4칸이라도 카드가 커진다. 격자 폭을
 * 쇼핑몰 본문(1200px − 좌우 여백)으로 묶어 카드 크기까지 같게 만든다.
 *
 * <h2>저장하기 전에는 아무것도 바뀌지 않는다</h2>
 * 끄는 동안에는 화면 안에서만 움직인다. 끌 때마다 저장하면 손이 미끄러진 것도 그대로
 * 나가고, 되돌릴 방법이 화면에 없다.
 */
export function AdminProductOrderView() {
  const { data: page, isLoading } = useQuery({
    queryKey: ['admin', 'display-order', 'products'],
    queryFn: () => productApi.list({ size: MAX_PRODUCTS }),
    staleTime: 0,
  });
  const { data: categories } = useAdminCategories();
  const reorder = useReorderProducts();

  /**
   * 끌어서 바꾼 순서. 손대기 전에는 null 이고, 그때는 서버가 준 차례를 그대로 쓴다.
   *
   * <p>서버 값을 상태로 복사해 두지 않는다. 복사하면 둘 중 어느 것이 지금 맞는지를
   * 코드가 계속 맞춰 줘야 하고, 저장 직후처럼 서버가 새 값을 줄 때 한 박자 어긋난다.
   */
  const [moved, setMoved] = useState<ProductSummary[] | null>(null);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [dragId, setDragId] = useState<number | null>(null);
  const [saved, setSaved] = useState(false);

  const fetched = page?.content;
  const order = moved ?? fetched ?? [];

  // 상품이 하나도 없는 분류는 탭으로 세우지 않는다. 눌러도 빈 화면만 나온다.
  const used = new Set(order.map((p) => p.categoryId));
  const tabs = (categories ?? []).filter((c) => used.has(c.id));

  const visible = categoryId == null ? order : order.filter((p) => p.categoryId === categoryId);

  const changed =
    fetched != null &&
    (order.length !== fetched.length || order.some((p, i) => p.id !== fetched[i]?.id));

  /**
   * 끌던 것을 target 자리에 내려놓는다.
   *
   * <p>보이는 목록 안에서 자리를 바꾸고, 그 결과를 <b>보이는 상품들이 원래 차지하던
   * 자리</b>에 다시 끼워 넣는다. 그래야 탭 안에서 끌어도 다른 분류 상품의 차례가
   * 밀리지 않는다.
   */
  const drop = (targetId: number) => {
    if (dragId == null || dragId === targetId) return;

    const from = visible.findIndex((p) => p.id === dragId);
    const to = visible.findIndex((p) => p.id === targetId);
    if (from < 0 || to < 0) return;

    const reordered = [...visible];
    reordered.splice(to, 0, ...reordered.splice(from, 1));

    const slots = order.flatMap((p, i) => (visible.some((v) => v.id === p.id) ? [i] : []));
    const next = [...order];
    slots.forEach((slot, i) => {
      next[slot] = reordered[i];
    });
    setMoved(next);
    setSaved(false);
  };

  const save = () => {
    setSaved(false);
    reorder.mutate(
      order.map((p) => p.id),
      {
        onSuccess: () => {
          // 서버 차례를 다시 따르게 한다. 목록은 훅이 무효화해 새로 받아 온다.
          setMoved(null);
          setSaved(true);
        },
      },
    );
  };

  return (
    <Panel
      title="진열 순서"
      note="손님이 보는 목록 그대로다. 끌어다 놓아 차례를 바꾼 뒤 저장한다. 저장하기 전에는 쇼핑몰이 바뀌지 않는다."
      actions={
        <>
          <Link href="/admin/products" className={styles.btn}>
            상품 목록
          </Link>
          <Button variant="gold" onClick={save} disabled={!changed || reorder.isPending}>
            {reorder.isPending ? '저장 중…' : '저장'}
          </Button>
        </>
      }
    >
      {reorder.error && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {reorder.error instanceof ApiError
            ? reorder.error.message
            : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {saved && !changed && (
        <Alert tone="success" title="저장했습니다">
          쇼핑몰 목록이 이 차례로 바뀌었습니다.
        </Alert>
      )}

      <div className={styles.orderStage}>
        <div className={styles.orderTabs}>
          <button
            type="button"
            className={categoryId == null ? styles.orderTabOn : styles.orderTab}
            onClick={() => setCategoryId(null)}
          >
            전체 <span className={styles.orderTabCount}>{order.length}</span>
          </button>
          {tabs.map((category) => (
            <button
              key={category.id}
              type="button"
              className={categoryId === category.id ? styles.orderTabOn : styles.orderTab}
              onClick={() => setCategoryId(category.id)}
            >
              {category.name}{' '}
              <span className={styles.orderTabCount}>
                {order.filter((p) => p.categoryId === category.id).length}
              </span>
            </button>
          ))}
        </div>

        {isLoading ? (
          <p className={styles.hint}>불러오는 중…</p>
        ) : visible.length === 0 ? (
          <p className={styles.hint}>이 분류에는 판매 중인 상품이 없습니다.</p>
        ) : (
          <ul className={styles.orderGrid}>
            {visible.map((product, index) => (
              <li
                key={product.id}
                draggable
                onDragStart={() => setDragId(product.id)}
                onDragEnd={() => setDragId(null)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => drop(product.id)}
                className={`${styles.orderCell} ${dragId === product.id ? styles.orderCellLift : ''}`}
              >
                <span className={styles.orderIndex}>{index + 1}</span>
                {/* 카드 안의 링크·찜 버튼이 눌리지 않게 막는다. 끌어다 놓는 화면에서
                    손이 미끄러지면 상세 페이지로 넘어가 버린다. */}
                <div className={styles.orderInert}>
                  <ProductCard product={product} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <p className={styles.hint}>
        숨김·판매종료 상품은 여기 없습니다 — 내걸리지 않은 것에는 진열 자리가 없습니다.
        다시 노출하면 원래 자리로 돌아옵니다.
      </p>
    </Panel>
  );
}
