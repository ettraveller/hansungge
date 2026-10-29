'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import * as productsApi from '@/api/admin/products';
import { Alert } from '@/components/admin/Alert';
import { Panel } from '@/components/admin/Panel';
import { ProductForm, type PendingImages } from '@/components/admin/products/ProductForm';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useCreateProduct } from '@/hooks/admin/useProducts';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

export function AdminProductNewView() {
  const router = useRouter();
  const { can } = useAdminSession();
  const create = useCreateProduct();

  const [attaching, setAttaching] = useState(false);
  /** 상품은 만들어졌는데 이미지만 못 붙은 상태. 그 상품 id 와 사유를 함께 들고 있는다. */
  const [attachFailed, setAttachFailed] = useState<{ productId: number; reason: string } | null>(
    null,
  );

  /**
   * 상품을 만든 뒤 올려 둔 이미지를 붙이고 상세로 보낸다.
   *
   * <p>이미지 연결은 상품 등록과 <b>같은 요청으로 묶을 수 없다.</b> 붙일 상대(상품 id)가
   * 등록 응답에서야 나오기 때문이다.
   *
   * <p>⚠️ 붙이기가 실패해도 <b>상품은 지우지 않는다.</b> 되돌리면 방금 입력한 이름·중량·
   * 공임이 전부 사라지는데, 실패한 원인(파일 문제)과 아무 관계가 없는 손해다.
   *
   * <p>그리고 <b>그 자리에 머문다.</b> 상세로 넘겨 버리면 화면이 바뀌면서 실패했다는
   * 사실이 사라지고, 사진이 왜 없는지 모르는 채로 판매를 시작하게 된다. 대신 폼을
   * 치우고 그 상품으로 가는 링크를 보여준다 — 폼이 남아 있으면 같은 상품을 또 등록한다.
   */
  const attachThenGo = async (productId: number, images?: PendingImages) => {
    const go = () => router.replace(`/admin/products/${productId}`);
    if (!images || (images.gallery.length === 0 && images.detail.length === 0)) {
      go();
      return;
    }

    setAttaching(true);
    try {
      // 대표이미지를 먼저 붙인다. 이쪽 첫 장이 thumbnailKey 가 되므로, 중간에 끊기면
      // 상세만 붙은 상태보다 목록에 사진이 있는 상태로 끊기는 쪽이 낫다.
      if (images.gallery.length > 0) {
        await productsApi.replaceImages(productId, 'GALLERY', images.gallery);
      }
      if (images.detail.length > 0) {
        await productsApi.replaceImages(productId, 'DETAIL', images.detail);
      }
      go();
    } catch (e) {
      setAttachFailed({
        productId,
        reason: e instanceof ApiError ? e.message : '이미지를 연결하지 못했습니다.',
      });
    } finally {
      setAttaching(false);
    }
  };

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

      {attachFailed ? (
        <Panel>
          <Alert tone="warning" title="상품은 등록됐습니다. 이미지만 붙지 않았습니다">
            {attachFailed.reason} 상세 화면의 「대표 이미지」·「상세 이미지」에서 다시 올려
            주세요. 상품은 아직 숨김 상태라 고객에게는 보이지 않습니다.
          </Alert>
          <div className={styles.formActions}>
            <Link
              className={`${styles.btn} ${styles.btnPrimary}`}
              href={`/admin/products/${attachFailed.productId}`}
            >
              등록한 상품으로 이동
            </Link>
            <Link className={styles.btn} href="/admin/products">
              목록
            </Link>
          </div>
        </Panel>
      ) : (
        <Panel>
          <ProductForm
            mode="create"
            pending={create.isPending || attaching}
            error={create.error}
            onCancel={() => router.push('/admin/products')}
            onSubmit={(values, images) =>
              create.mutate(values, {
                onSuccess: (product) => void attachThenGo(product.id, images),
              })
            }
          />
        </Panel>
      )}
    </div>
  );
}
