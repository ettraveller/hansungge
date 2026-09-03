'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';

import { Alert } from '@/components/admin/Alert';
import { Panel } from '@/components/admin/Panel';
import { ProductForm } from '@/components/admin/products/ProductForm';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useCreateProduct } from '@/hooks/admin/useProducts';
import styles from '@/components/admin/admin.module.css';

export function AdminProductNewView() {
  const router = useRouter();
  const { can } = useAdminSession();
  const create = useCreateProduct();

  if (!can('MANAGER')) {
    return (
      <div className={styles.page}>
        <Alert tone="warning" title="권한이 없습니다">
          상품 등록은 매니저(MANAGER) 이상만 할 수 있습니다.
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
          <h1 className={styles.title}>상품 등록</h1>
          <p className={styles.subtitle}>
            판매가는 입력하지 않는다 — 시세 × 중량 + 공임으로 서버가 계산한다.
          </p>
        </div>
        <div className={styles.headActions}>
          <Link className={styles.btn} href="/admin/products">
            목록
          </Link>
        </div>
      </header>

      <Panel>
        <ProductForm
          mode="create"
          pending={create.isPending}
          error={create.error}
          onCancel={() => router.push('/admin/products')}
          onSubmit={(values) =>
            create.mutate(values, {
              // 등록 직후에는 숨김 상태다. 바로 상세로 보내 재고 확인 → 판매중 전환을 잇는다.
              onSuccess: (product) => router.replace(`/admin/products/${product.id}`),
            })
          }
        />
      </Panel>
    </div>
  );
}
