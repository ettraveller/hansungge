'use client';

import { useState } from 'react';
import Link from 'next/link';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Panel } from '@/components/admin/Panel';
import { useAdminCategories } from '@/hooks/admin/useCategories';
import { useAdminProducts, useReorderProducts } from '@/hooks/admin/useProducts';
import { mediaUrl } from '@/utils/media';
import { PRODUCT_STATUS_LABEL } from '@/types/product';
import type { AdminProduct } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/** 한 번에 끌어다 놓을 수 있는 최대 개수. 넘으면 저장은 되지만 화면이 길어진다. */
const MAX_PRODUCTS = 500;

/**
 * 진열 순서 — 쇼핑몰 목록에 서는 차례를 끌어다 놓아 정한다.
 *
 * <h2>탭마다 따로 두지 않는다</h2>
 * 상품은 카테고리를 하나만 가진다. 그래서 전체 순서 하나면 모든 탭의 차례가 정해지고,
 * 「골드바」 탭은 그 순서에서 골드바만 걸러낸 것이 된다. 탭 안에서 끌어도 되고, 그 결과는
 * 전체 순서에 그대로 반영된다 — 걸러진 자리들끼리만 자리를 바꾼다.
 *
 * <h2>저장하기 전에는 아무것도 바뀌지 않는다</h2>
 * 끄는 동안에는 화면 안에서만 움직인다. 쇼핑몰은 [저장]을 누른 뒤에 바뀐다. 끌 때마다
 * 저장하면 손이 미끄러진 것도 그대로 반영되고, 되돌릴 방법이 화면에 없다.
 *
 * <h2>숨김 상품도 보여준다</h2>
 * 지금 안 보이는 상품도 자리는 있어야 한다. 자리가 없으면 다시 노출하는 순간 맨 앞에
 * 끼어든다. 대신 흐리게 그려 「이건 지금 손님에게 안 보인다」를 알려준다.
 */
export function AdminProductOrderView() {
  const { data: page, isLoading } = useAdminProducts({ size: MAX_PRODUCTS });
  const { data: categories } = useAdminCategories();
  const reorder = useReorderProducts();

  /**
   * 끌어서 바꾼 순서. 아직 손대지 않았으면 null 이고, 그때는 서버가 준 차례를 그대로 쓴다.
   *
   * <p>서버 값을 상태로 복사해 두지 않는다. 복사하면 둘 중 어느 것이 지금 맞는지를
   * 코드가 계속 맞춰 줘야 하고, 저장 직후처럼 서버가 새 값을 줄 때 한 박자 어긋난다.
   */
  const [moved, setMoved] = useState<AdminProduct[] | null>(null);
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
   * 자리</b>에 다시 끼워 넣는다. 그래야 탭 안에서 끌어도 다른 카테고리 상품의 차례가
   * 밀리지 않는다.
   */
  const drop = (targetId: number) => {
    if (dragId == null || dragId === targetId) return;

    const from = visible.findIndex((p) => p.id === dragId);
    const to = visible.findIndex((p) => p.id === targetId);
    if (from < 0 || to < 0) return;

    const moved = [...visible];
    moved.splice(to, 0, ...moved.splice(from, 1));

    const slots = order.flatMap((p, i) => (visible.some((v) => v.id === p.id) ? [i] : []));
    const next = [...order];
    slots.forEach((slot, i) => {
      next[slot] = moved[i];
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
    <div>
      <Panel
        title="진열 순서"
        note="끌어다 놓아 차례를 바꾼 뒤 저장한다. 저장하기 전에는 쇼핑몰이 바뀌지 않는다."
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
          <p className={styles.hint}>이 분류에는 상품이 없습니다.</p>
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
                className={`${styles.orderCard} ${dragId === product.id ? styles.orderCardLift : ''} ${
                  product.active && product.status === 'ON_SALE' ? '' : styles.orderCardOff
                }`}
              >
                <span className={styles.orderIndex}>{index + 1}</span>
                <div className={styles.orderThumb}>
                  <Thumb objectKey={product.thumbnailKey} />
                </div>
                <p className={styles.orderName}>{product.name}</p>
                <p className={styles.orderMeta}>
                  {PRODUCT_STATUS_LABEL[product.status]}
                  {!product.active && ' · 비활성'}
                </p>
              </li>
            ))}
          </ul>
        )}

        {order.length >= MAX_PRODUCTS && (
          <Alert tone="warning" title="상품이 많습니다">
            이 화면은 {MAX_PRODUCTS}개까지 불러옵니다. 그보다 많아지면 여기서 전체 순서를 정할
            수 없습니다.
          </Alert>
        )}
      </Panel>
    </div>
  );
}

/** 대표이미지. 없거나 주소를 만들 수 없으면 글자로 대신한다 — 깨진 그림을 두지 않는다. */
function Thumb({ objectKey }: { objectKey?: string | null }) {
  const url = objectKey ? mediaUrl(objectKey) : null;
  if (!url) return <span className={styles.orderNoThumb}>사진 없음</span>;
  // 관리자 화면이고 수가 적다. next/image 를 쓰면 썸네일마다 최적화 요청이 생기는데,
  // 여기서 얻을 것이 없다.
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={url} alt="" draggable={false} />;
}
