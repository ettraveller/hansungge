'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Panel } from '@/components/admin/Panel';
import { ProductForm } from '@/components/admin/products/ProductForm';
import { ProductImagesPanel } from '@/components/admin/products/ProductImagesPanel';
import { ProductOptionsPanel } from '@/components/admin/products/ProductOptionsPanel';
import { ProductPricePreview } from '@/components/admin/products/ProductPricePreview';
import { ProductStatusPanel } from '@/components/admin/products/ProductStatusPanel';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useAdminProduct, useUpdateProduct } from '@/hooks/admin/useProducts';
import { formatDateTime } from '@/utils/format';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/** 상품 상세 · 수정. 가격 재료를 바꾸면 미리보기가 즉시 다시 계산된다. */
export function AdminProductDetailView({ productId }: { productId: number }) {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const { data: product, isLoading, error } = useAdminProduct(productId);
  const update = useUpdateProduct();
  const [editing, setEditing] = useState(false);

  if (isLoading) {
    return <p className={styles.loading}>불러오는 중…</p>;
  }

  if (error || !product) {
    return (
      <div className={styles.page}>
        <Alert tone="danger" title="상품을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '존재하지 않는 상품일 수 있습니다.'}
        </Alert>
        <div>
          <Link className={styles.btn} href="/admin/products">
            목록으로
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>{product.name}</h1>
          <p className={styles.subtitle}>
            #{product.id} · {product.priceTypeName} · 등록 {product.createdBy ?? '-'}{' '}
            {formatDateTime(product.createdAt)} · 최근 수정 {product.updatedBy ?? '-'}{' '}
            {formatDateTime(product.updatedAt)}
          </p>
        </div>
        <div className={styles.headActions}>
          <Link className={styles.btn} href="/admin/products">
            목록
          </Link>
          <Link className={styles.btn} href={`/shop/product/${product.id}`} target="_blank">
            쇼핑몰에서 보기
          </Link>
          {canEdit && (
            <Button variant={editing ? 'default' : 'gold'} onClick={() => setEditing(!editing)}>
              {editing ? '편집 닫기' : '기본정보 수정'}
            </Button>
          )}
        </div>
      </header>

      {editing && canEdit && (
        <Panel
          title="기본정보 수정"
          note="가격 재료(시세 연동·중량·공임·배수)를 바꾸면 판매가가 즉시 달라진다."
        >
          <ProductForm
            mode="edit"
            initial={product}
            pending={update.isPending}
            error={update.error}
            onCancel={() => setEditing(false)}
            onSubmit={(values) =>
              update.mutate(
                { id: product.id, input: values },
                { onSuccess: () => setEditing(false) },
              )
            }
          />
        </Panel>
      )}

      <ProductStatusPanel product={product} canEdit={canEdit} />
      <ProductPricePreview productId={product.id} />
      <ProductOptionsPanel
        productId={product.id}
        priceType={product.priceType}
        canEdit={canEdit}
      />
      <ProductImagesPanel productId={product.id} canEdit={canEdit} />
    </div>
  );
}
